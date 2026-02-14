package com.gengzi.agentteams.service;

import com.gengzi.agentteams.domain.TaskStatus;
import com.gengzi.agentteams.domain.TeamMessage;
import com.gengzi.agentteams.domain.TeamTask;
import com.gengzi.agentteams.domain.TeamWorkspace;
import com.gengzi.agentteams.domain.TeammateAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AgentTaskRunnerService {

    private static final Logger log = LoggerFactory.getLogger(AgentTaskRunnerService.class);

    private final TeamRegistryService teamRegistryService;
    private final ChatClient.Builder chatClientBuilder;
    private final LlmRetryService llmRetryService;

    public AgentTaskRunnerService(
            TeamRegistryService teamRegistryService,
            ChatClient.Builder chatClientBuilder,
            LlmRetryService llmRetryService
    ) {
        this.teamRegistryService = teamRegistryService;
        this.chatClientBuilder = chatClientBuilder;
        this.llmRetryService = llmRetryService;
    }

    public String runTask(String teamId, String taskId, String teammateIdOverride) {
        log.info("开始执行任务，teamId={}，taskId={}，teammateIdOverride={}", teamId, taskId, teammateIdOverride);

        TeamWorkspace team = teamRegistryService.getTeam(teamId);
        TeamTask task;
        TeammateAgent teammate;
        List<TeamMessage> unread;

        synchronized (team) {
            task = teamRegistryService.getTask(team, taskId);
            String teammateId = teammateIdOverride;
            if (task.getStatus() == TaskStatus.PENDING) {
                if (!teamRegistryService.isTaskReady(team, task)) {
                    log.warn("任务依赖未完成，无法执行，teamId={}，taskId={}", teamId, taskId);
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Task dependencies are not completed");
                }
                if (teammateId == null || teammateId.isBlank()) {
                    teammateId = task.getAssigneeId();
                }
                if (teammateId == null || teammateId.isBlank()) {
                    teammateId = selectTeammateForClaim(team);
                    log.info("未指定执行人，自动分配，teamId={}，taskId={}，assigneeId={}", teamId, taskId, teammateId);
                }
                teammate = teamRegistryService.getTeammate(team, teammateId);
                task.setAssigneeId(teammate.getId());
                task.setStatus(TaskStatus.IN_PROGRESS);
            } else {
                if (teammateId == null || teammateId.isBlank()) {
                    teammateId = task.getAssigneeId();
                }
                if (teammateId == null || teammateId.isBlank()) {
                    log.warn("任务无指派人，无法执行，teamId={}，taskId={}", teamId, taskId);
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task has no assignee, claim it first");
                }
                teammate = teamRegistryService.getTeammate(team, teammateId);
            }
            if (task.getStatus() != TaskStatus.IN_PROGRESS) {
                log.warn("任务状态不允许执行，teamId={}，taskId={}，status={}", teamId, taskId, task.getStatus());
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not executable in current state");
            }

            unread = teamRegistryService.consumeUnreadMessages(team, teammate);
        }

        String prompt = buildPrompt(team, task, teammate, unread);
        ChatClient chatClient = chatClientBuilder.build();
        String output = llmRetryService.executeWithRetry(() -> chatClient.prompt()
                .system(systemPrompt(teammate))
                .user(prompt)
                .call()
                .content());

        synchronized (team) {
            teammate.getHistory().add("TASK: " + task.getTitle() + "\n" + task.getDescription());
            teammate.getHistory().add("OUTPUT: " + output);
            teamRegistryService.completeTask(teamId, taskId, teammate.getId(), output);
        }
        log.info("任务执行完成，teamId={}，taskId={}，assigneeId={}", teamId, taskId, teammate.getId());
        return output;
    }

    private String systemPrompt(TeammateAgent teammate) {
        return "You are a specialized teammate in an AI agent team. "
                + "Role: " + teammate.getRole() + ". "
                + "Work in small, concrete steps and output actionable results. "
                + "If context is missing, state assumptions explicitly.";
    }

    private String buildPrompt(TeamWorkspace team, TeamTask task, TeammateAgent teammate, List<TeamMessage> unread) {
        String dependencyContext = task.getDependencyTaskIds().stream()
                .map(id -> {
                    TeamTask dep = team.getTasks().get(id);
                    String result = dep == null ? "" : (dep.getResult() == null ? "" : dep.getResult());
                    return "Dependency " + id + ": " + result;
                })
                .collect(Collectors.joining("\n"));

        String mailboxContext = unread.isEmpty()
                ? "No unread teammate messages."
                : unread.stream().map(message -> "From " + message.fromId() + ": " + message.content())
                .collect(Collectors.joining("\n"));

        String historyContext = teammate.getHistory().stream()
                .skip(Math.max(0, teammate.getHistory().size() - 6))
                .collect(Collectors.joining("\n"));

        return "Team objective:\n" + team.getObjective() + "\n\n"
                + "Task title: " + task.getTitle() + "\n"
                + "Task details:\n" + task.getDescription() + "\n\n"
                + "Dependency outputs:\n" + dependencyContext + "\n\n"
                + "Unread mailbox:\n" + mailboxContext + "\n\n"
                + "Recent personal context:\n" + historyContext + "\n\n"
                + "Return a concise deliverable for this task.";
    }

    private String selectTeammateForClaim(TeamWorkspace team) {
        List<TeammateAgent> all = team.getTeammates().values().stream().toList();
        if (all.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No teammates available");
        }
        List<TeammateAgent> executors = all.stream()
                .filter(teammate -> !isLeaderRole(teammate.getRole()))
                .toList();
        if (executors.isEmpty()) {
            executors = all;
        }
        Map<String, Long> inProgressCountByAssignee = team.getTasks().values().stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS)
                .filter(t -> t.getAssigneeId() != null && !t.getAssigneeId().isBlank())
                .collect(Collectors.groupingBy(TeamTask::getAssigneeId, Collectors.counting()));

        return executors.stream()
                .min(Comparator
                        .comparingLong((TeammateAgent t) -> inProgressCountByAssignee.getOrDefault(t.getId(), 0L))
                        .thenComparing(TeammateAgent::getId))
                .map(TeammateAgent::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No executable teammate found"));
    }

    private boolean isLeaderRole(String role) {
        if (role == null) {
            return false;
        }
        String lower = role.toLowerCase(Locale.ROOT);
        return lower.contains("leader")
                || lower.contains("lead")
                || lower.contains("manager")
                || lower.contains("planner")
                || lower.contains("orchestrator");
    }
}

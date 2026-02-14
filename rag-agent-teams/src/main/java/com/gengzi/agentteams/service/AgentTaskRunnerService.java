package com.gengzi.agentteams.service;

import com.gengzi.agentteams.domain.TaskStatus;
import com.gengzi.agentteams.domain.TeamMessage;
import com.gengzi.agentteams.domain.TeamTask;
import com.gengzi.agentteams.domain.TeamWorkspace;
import com.gengzi.agentteams.domain.TeammateAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AgentTaskRunnerService {

    // 团队状态与任务流转服务
    private final TeamRegistryService teamRegistryService;
    // Spring AI ChatClient 构建器（由 starter 自动注入）
    private final ChatClient.Builder chatClientBuilder;

    public AgentTaskRunnerService(TeamRegistryService teamRegistryService, ChatClient.Builder chatClientBuilder) {
        this.teamRegistryService = teamRegistryService;
        this.chatClientBuilder = chatClientBuilder;
    }

    // 执行指定任务：组装上下文 -> 调模型 -> 回写任务结果
    public String runTask(String teamId, String taskId, String teammateIdOverride) {
        TeamWorkspace team = teamRegistryService.getTeam(teamId);
        TeamTask task;
        TeammateAgent teammate;
        List<TeamMessage> unread;

        synchronized (team) {
            task = teamRegistryService.getTask(team, taskId);
            String teammateId = teammateIdOverride;
            if (teammateId == null || teammateId.isBlank()) {
                teammateId = task.getAssigneeId();
            }
            if (teammateId == null || teammateId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task has no assignee, claim it first");
            }
            teammate = teamRegistryService.getTeammate(team, teammateId);

            if (task.getStatus() == TaskStatus.PENDING) {
                // 允许“未显式 claim”直接执行，但依赖必须已完成
                if (!teamRegistryService.isTaskReady(team, task)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Task dependencies are not completed");
                }
                task.setAssigneeId(teammate.getId());
                task.setStatus(TaskStatus.IN_PROGRESS);
            }
            if (task.getStatus() != TaskStatus.IN_PROGRESS) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not executable in current state");
            }

            unread = teamRegistryService.consumeUnreadMessages(team, teammate);
        }

        // 将团队目标、依赖结果、邮箱消息和个人历史拼成执行提示词
        String prompt = buildPrompt(team, task, teammate, unread);
        ChatClient chatClient = chatClientBuilder.build();
        String output = chatClient.prompt()
                .system(systemPrompt(teammate))
                .user(prompt)
                .call()
                .content();

        synchronized (team) {
            // 写入 teammate 私有历史，便于后续任务继承上下文
            teammate.getHistory().add("TASK: " + task.getTitle() + "\n" + task.getDescription());
            teammate.getHistory().add("OUTPUT: " + output);
            teamRegistryService.completeTask(teamId, taskId, teammate.getId(), output);
        }
        return output;
    }

    // 系统提示：约束该 teammate 的角色和输出风格
    private String systemPrompt(TeammateAgent teammate) {
        return "You are a specialized teammate in an AI agent team. "
                + "Role: " + teammate.getRole() + ". "
                + "Work in small, concrete steps and output actionable results. "
                + "If context is missing, state assumptions explicitly.";
    }

    // 用户提示：拼接可执行上下文（依赖、消息、历史）
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
                : unread.stream()
                .map(message -> "From " + message.fromId() + ": " + message.content())
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
}

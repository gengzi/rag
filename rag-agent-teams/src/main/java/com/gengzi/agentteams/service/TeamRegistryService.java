package com.gengzi.agentteams.service;

import com.gengzi.agentteams.api.CreateTeamRequest;
import com.gengzi.agentteams.api.TeamStateResponse;
import com.gengzi.agentteams.domain.TaskStatus;
import com.gengzi.agentteams.domain.TeamMessage;
import com.gengzi.agentteams.domain.TeamTask;
import com.gengzi.agentteams.domain.TeammateAgent;
import com.gengzi.agentteams.domain.TeamWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TeamRegistryService {

    private static final Logger log = LoggerFactory.getLogger(TeamRegistryService.class);

    private final Map<String, TeamWorkspace> teams = new ConcurrentHashMap<>();
    private final String configuredModel;

    public TeamRegistryService(@Value("${spring.ai.openai.chat.options.model:gemini-3-pro-low}") String configuredModel) {
        this.configuredModel = configuredModel;
    }

    public TeamWorkspace createTeam(String name, String objective, List<CreateTeamRequest.TeammateSpec> teammateSpecs) {
        TeamWorkspace team = new TeamWorkspace(name, objective);
        for (CreateTeamRequest.TeammateSpec spec : teammateSpecs) {
            TeammateAgent teammate = new TeammateAgent(spec.getName(), spec.getRole(), configuredModel);
            team.getTeammates().put(teammate.getId(), teammate);
        }
        ensureLeaderPresent(team);
        teams.put(team.getId(), team);
        log.info("创建团队成功，teamId={}，name={}，teammateCount={}，model={}，planVersion={}",
                team.getId(), team.getName(), team.getTeammates().size(), configuredModel, team.getPlanVersion());
        return team;
    }

    public TeamTask createTask(String teamId, String title, String description, List<String> dependencies, String assigneeId) {
        TeamWorkspace team = getTeam(teamId);
        synchronized (team) {
            if (assigneeId != null && !assigneeId.isBlank() && !team.getTeammates().containsKey(assigneeId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown assigneeId");
            }
            for (String dependency : dependencies) {
                if (!team.getTasks().containsKey(dependency)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dependency task not found: " + dependency);
                }
            }
            TeamTask task = new TeamTask(title, description, dependencies, assigneeId);
            team.getTasks().put(task.getId(), task);
            long planVersion = team.bumpPlanVersion();
            log.info("创建任务成功，teamId={}，taskId={}，title={}，assigneeId={}，dependencyCount={}，planVersion={}",
                    teamId, task.getId(), title, assigneeId, dependencies == null ? 0 : dependencies.size(), planVersion);
            return task;
        }
    }

    public TeamTask updateTaskPlan(
            String teamId,
            String taskId,
            String title,
            String description,
            List<String> dependencies,
            String assigneeId
    ) {
        TeamWorkspace team = getTeam(teamId);
        synchronized (team) {
            TeamTask task = getTask(team, taskId);
            if (task.getStatus() != TaskStatus.PENDING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending task can be updated");
            }
            if (assigneeId != null && !assigneeId.isBlank() && !team.getTeammates().containsKey(assigneeId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown assigneeId");
            }
            if (dependencies != null) {
                for (String dependencyId : dependencies) {
                    if (!team.getTasks().containsKey(dependencyId)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dependency task not found: " + dependencyId);
                    }
                    if (taskId.equals(dependencyId)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task cannot depend on itself");
                    }
                }
                task.replaceDependencies(dependencies);
            }
            if (title != null && !title.isBlank()) {
                task.setTitle(title);
            }
            if (description != null && !description.isBlank()) {
                task.setDescription(description);
            }
            if (assigneeId != null && !assigneeId.isBlank()) {
                task.setAssigneeId(assigneeId);
            }
            long planVersion = team.bumpPlanVersion();
            log.info("更新任务计划，teamId={}，taskId={}，title={}，assigneeId={}，planVersion={}",
                    teamId, taskId, task.getTitle(), task.getAssigneeId(), planVersion);
            return task;
        }
    }

    public void deleteTaskPlan(String teamId, String taskId) {
        TeamWorkspace team = getTeam(teamId);
        synchronized (team) {
            TeamTask task = getTask(team, taskId);
            if (task.getStatus() != TaskStatus.PENDING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending task can be deleted");
            }
            boolean referenced = team.getTasks().values().stream()
                    .anyMatch(candidate -> !candidate.getId().equals(taskId)
                            && candidate.getDependencyTaskIds().contains(taskId)
                            && candidate.getStatus() != TaskStatus.COMPLETED);
            if (referenced) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is referenced by other active tasks");
            }
            team.getTasks().remove(taskId);
            long planVersion = team.bumpPlanVersion();
            log.info("删除任务计划，teamId={}，taskId={}，planVersion={}", teamId, taskId, planVersion);
        }
    }

    public TeamTask claimTask(String teamId, String taskId, String teammateId) {
        TeamWorkspace team = getTeam(teamId);
        synchronized (team) {
            TeammateAgent teammate = getTeammate(team, teammateId);
            TeamTask task = getTask(team, taskId);
            if (task.getStatus() != TaskStatus.PENDING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not pending");
            }
            if (!isTaskReady(team, task)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Task dependencies are not completed");
            }
            task.setAssigneeId(teammate.getId());
            task.setStatus(TaskStatus.IN_PROGRESS);
            log.info("认领任务成功，teamId={}，taskId={}，teammateId={}，planVersion={}",
                    teamId, taskId, teammateId, team.getPlanVersion());
            return task;
        }
    }

    public TeamTask completeTask(String teamId, String taskId, String teammateId, String result) {
        TeamWorkspace team = getTeam(teamId);
        synchronized (team) {
            getTeammate(team, teammateId);
            TeamTask task = getTask(team, taskId);
            if (task.getStatus() != TaskStatus.IN_PROGRESS) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not in progress");
            }
            if (!teammateId.equals(task.getAssigneeId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Only assignee can complete this task");
            }
            task.setResult(result);
            task.setStatus(TaskStatus.COMPLETED);
            log.info("任务完成，teamId={}，taskId={}，teammateId={}，resultSize={}，planVersion={}",
                    teamId, taskId, teammateId, result == null ? 0 : result.length(), team.getPlanVersion());
            return task;
        }
    }

    public TeamMessage sendMessage(String teamId, String fromId, String toId, String content) {
        TeamWorkspace team = getTeam(teamId);
        synchronized (team) {
            getTeammate(team, fromId);
            getTeammate(team, toId);
            TeamMessage message = new TeamMessage(fromId, toId, content, Instant.now());
            team.getMailbox().add(message);
            log.info("发送团队消息，teamId={}，from={}，to={}，contentSize={}，planVersion={}",
                    teamId, fromId, toId, content == null ? 0 : content.length(), team.getPlanVersion());
            return message;
        }
    }

    public List<TeamMessage> consumeUnreadMessages(TeamWorkspace team, TeammateAgent teammate) {
        synchronized (team) {
            List<TeamMessage> mailbox = team.getMailbox();
            List<TeamMessage> unread = new ArrayList<>();
            for (int i = teammate.getMailboxCursor(); i < mailbox.size(); i++) {
                TeamMessage message = mailbox.get(i);
                if (teammate.getId().equals(message.toId())) {
                    unread.add(message);
                }
            }
            teammate.setMailboxCursor(mailbox.size());
            if (!unread.isEmpty()) {
                log.info("消费未读消息，teamId={}，teammateId={}，count={}，planVersion={}",
                        team.getId(), teammate.getId(), unread.size(), team.getPlanVersion());
            }
            return unread;
        }
    }

    public TeamWorkspace getTeam(String teamId) {
        TeamWorkspace team = teams.get(teamId);
        if (team == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found");
        }
        synchronized (team) {
            ensureLeaderPresent(team);
        }
        return team;
    }

    public TeamStateResponse getState(String teamId) {
        TeamWorkspace team = getTeam(teamId);
        synchronized (team) {
            TeamStateResponse response = new TeamStateResponse();
            response.setId(team.getId());
            response.setName(team.getName());
            response.setObjective(team.getObjective());
            response.setCreatedAt(team.getCreatedAt());
            response.setPlanVersion(team.getPlanVersion());

            List<TeamStateResponse.TeammateView> teammateViews = team.getTeammates().values().stream().map(teammate -> {
                TeamStateResponse.TeammateView view = new TeamStateResponse.TeammateView();
                view.setId(teammate.getId());
                view.setName(teammate.getName());
                view.setRole(teammate.getRole());
                view.setModel(teammate.getModel());
                return view;
            }).toList();
            response.setTeammates(teammateViews);

            List<TeamStateResponse.TaskView> taskViews = team.getTasks().values().stream()
                    .sorted(Comparator.comparing(TeamTask::getCreatedAt))
                    .map(task -> {
                        TeamStateResponse.TaskView view = new TeamStateResponse.TaskView();
                        view.setId(task.getId());
                        view.setTitle(task.getTitle());
                        view.setDescription(task.getDescription());
                        view.setDependencies(task.getDependencyTaskIds());
                        view.setStatus(task.getStatus());
                        view.setAssigneeId(task.getAssigneeId());
                        view.setResult(task.getResult());
                        return view;
                    })
                    .toList();
            response.setTasks(taskViews);
            return response;
        }
    }

    public TeammateAgent getTeammate(TeamWorkspace team, String teammateId) {
        TeammateAgent teammate = team.getTeammates().get(teammateId);
        if (teammate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Teammate not found");
        }
        return teammate;
    }

    public TeamTask getTask(TeamWorkspace team, String taskId) {
        TeamTask task = team.getTasks().get(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return task;
    }

    public boolean isTaskReady(TeamWorkspace team, TeamTask task) {
        for (String dependencyId : task.getDependencyTaskIds()) {
            TeamTask dependencyTask = team.getTasks().get(dependencyId);
            if (dependencyTask == null || dependencyTask.getStatus() != TaskStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    private void ensureLeaderPresent(TeamWorkspace team) {
        boolean hasLeader = team.getTeammates().values().stream().anyMatch(teammate -> isLeaderRole(teammate.getRole()));
        if (hasLeader) {
            return;
        }
        TeammateAgent leader = new TeammateAgent("Team Leader", "Leader", configuredModel);
        team.getTeammates().put(leader.getId(), leader);
        log.info("团队缺少leader，已自动补齐，teamId={}，leaderId={}，planVersion={}",
                team.getId(), leader.getId(), team.getPlanVersion());
    }

    private boolean isLeaderRole(String role) {
        if (role == null) {
            return false;
        }
        String lower = role.toLowerCase();
        return lower.contains("leader")
                || lower.contains("lead")
                || lower.contains("manager")
                || lower.contains("planner")
                || lower.contains("orchestrator");
    }
}

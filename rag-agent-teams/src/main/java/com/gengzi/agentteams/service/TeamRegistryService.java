package com.gengzi.agentteams.service;

import com.gengzi.agentteams.api.CreateTeamRequest;
import com.gengzi.agentteams.api.TeamStateResponse;
import com.gengzi.agentteams.domain.TaskStatus;
import com.gengzi.agentteams.domain.TeamMessage;
import com.gengzi.agentteams.domain.TeamTask;
import com.gengzi.agentteams.domain.TeamWorkspace;
import com.gengzi.agentteams.domain.TeammateAgent;
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

    // 存放所有的team
    private final Map<String, TeamWorkspace> teams = new ConcurrentHashMap<>();

    public TeamWorkspace createTeam(String name, String objective, List<CreateTeamRequest.TeammateSpec> teammateSpecs) {
        TeamWorkspace team = new TeamWorkspace(name, objective);
        for (CreateTeamRequest.TeammateSpec spec : teammateSpecs) {
            TeammateAgent teammate = new TeammateAgent(spec.getName(), spec.getRole(), spec.getModel());
            team.getTeammates().put(teammate.getId(), teammate);
        }
        teams.put(team.getId(), team);
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
            return task;
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
            return unread;
        }
    }

    public TeamWorkspace getTeam(String teamId) {
        TeamWorkspace team = teams.get(teamId);
        if (team == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found");
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

            List<TeamStateResponse.TeammateView> teammateViews = team.getTeammates().values().stream()
                    .map(teammate -> {
                        TeamStateResponse.TeammateView view = new TeamStateResponse.TeammateView();
                        view.setId(teammate.getId());
                        view.setName(teammate.getName());
                        view.setRole(teammate.getRole());
                        view.setModel(teammate.getModel());
                        return view;
                    })
                    .toList();
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
}

package com.gengzi.agentteams.api;

import com.gengzi.agentteams.domain.TeamMessage;
import com.gengzi.agentteams.domain.TeamTask;
import com.gengzi.agentteams.domain.TeamWorkspace;
import com.gengzi.agentteams.service.AgentTaskRunnerService;
import com.gengzi.agentteams.service.TeamRegistryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/teams")
public class AgentTeamsController {

    private final TeamRegistryService teamRegistryService;
    private final AgentTaskRunnerService agentTaskRunnerService;

    public AgentTeamsController(TeamRegistryService teamRegistryService, AgentTaskRunnerService agentTaskRunnerService) {
        this.teamRegistryService = teamRegistryService;
        this.agentTaskRunnerService = agentTaskRunnerService;
    }

    @PostMapping
    public TeamStateResponse createTeam(@Valid @RequestBody CreateTeamRequest request) {
        TeamWorkspace team = teamRegistryService.createTeam(request.getName(), request.getObjective(), request.getTeammates());
        return teamRegistryService.getState(team.getId());
    }

    @GetMapping("/{teamId}")
    public TeamStateResponse getTeam(@PathVariable String teamId) {
        return teamRegistryService.getState(teamId);
    }

    @PostMapping("/{teamId}/tasks")
    public TeamTask createTask(@PathVariable String teamId, @Valid @RequestBody CreateTaskRequest request) {
        return teamRegistryService.createTask(
                teamId,
                request.getTitle(),
                request.getDescription(),
                request.getDependencies(),
                request.getAssigneeId()
        );
    }

    @PostMapping("/{teamId}/tasks/{taskId}/claim")
    public TeamTask claimTask(@PathVariable String teamId, @PathVariable String taskId, @Valid @RequestBody ClaimTaskRequest request) {
        return teamRegistryService.claimTask(teamId, taskId, request.getTeammateId());
    }

    @PostMapping("/{teamId}/tasks/{taskId}/complete")
    public TeamTask completeTask(@PathVariable String teamId, @PathVariable String taskId, @Valid @RequestBody CompleteTaskRequest request) {
        return teamRegistryService.completeTask(teamId, taskId, request.getTeammateId(), request.getResult());
    }

    @PostMapping("/{teamId}/messages")
    public TeamMessage sendMessage(@PathVariable String teamId, @Valid @RequestBody MessageRequest request) {
        return teamRegistryService.sendMessage(teamId, request.getFromId(), request.getToId(), request.getContent());
    }

    @PostMapping("/{teamId}/tasks/{taskId}/run")
    public Map<String, String> runTask(@PathVariable String teamId, @PathVariable String taskId, @RequestBody(required = false) RunTaskRequest request) {
        String teammateId = request == null ? null : request.getTeammateId();
        String output = agentTaskRunnerService.runTask(teamId, taskId, teammateId);
        return Map.of("taskId", taskId, "output", output);
    }
}

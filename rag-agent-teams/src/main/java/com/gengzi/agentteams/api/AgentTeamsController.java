package com.gengzi.agentteams.api;

import com.gengzi.agentteams.domain.TeamMessage;
import com.gengzi.agentteams.domain.TeamTask;
import com.gengzi.agentteams.domain.TeamWorkspace;
import com.gengzi.agentteams.service.AgentTaskRunnerService;
import com.gengzi.agentteams.service.TeamRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Agent Teams", description = "基于 Spring AI 的团队编排接口（团队、任务、消息、执行）")
@RestController
@RequestMapping("/api/teams")
public class AgentTeamsController {

    // 负责 team/task/message 的增删改查与状态流转
    private final TeamRegistryService teamRegistryService;
    // 负责调用大模型执行任务
    private final AgentTaskRunnerService agentTaskRunnerService;

    public AgentTeamsController(TeamRegistryService teamRegistryService, AgentTaskRunnerService agentTaskRunnerService) {
        this.teamRegistryService = teamRegistryService;
        this.agentTaskRunnerService = agentTaskRunnerService;
    }

    @Operation(summary = "创建团队", description = "创建一个团队，并初始化团队目标与成员列表")
    @PostMapping
    public TeamStateResponse createTeam(@Valid @RequestBody CreateTeamRequest request) {
        TeamWorkspace team = teamRegistryService.createTeam(request.getName(), request.getObjective(), request.getTeammates());
        return teamRegistryService.getState(team.getId());
    }

    @Operation(summary = "查询团队状态", description = "返回团队信息、成员列表、任务列表")
    @GetMapping("/{teamId}")
    public TeamStateResponse getTeam(@PathVariable String teamId) {
        return teamRegistryService.getState(teamId);
    }

    @Operation(summary = "创建任务", description = "创建任务，可携带依赖任务ID和初始 assignee")
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

    @Operation(summary = "认领任务", description = "成员认领任务。仅当依赖任务全部完成时允许认领")
    @PostMapping("/{teamId}/tasks/{taskId}/claim")
    public TeamTask claimTask(@PathVariable String teamId, @PathVariable String taskId, @Valid @RequestBody ClaimTaskRequest request) {
        return teamRegistryService.claimTask(teamId, taskId, request.getTeammateId());
    }

    @Operation(summary = "手动完成任务", description = "成员提交任务结果并将状态置为 COMPLETED")
    @PostMapping("/{teamId}/tasks/{taskId}/complete")
    public TeamTask completeTask(@PathVariable String teamId, @PathVariable String taskId, @Valid @RequestBody CompleteTaskRequest request) {
        return teamRegistryService.completeTask(teamId, taskId, request.getTeammateId(), request.getResult());
    }

    @Operation(summary = "发送团队消息", description = "成员之间通过团队邮箱发送消息")
    @PostMapping("/{teamId}/messages")
    public TeamMessage sendMessage(@PathVariable String teamId, @Valid @RequestBody MessageRequest request) {
        return teamRegistryService.sendMessage(teamId, request.getFromId(), request.getToId(), request.getContent());
    }

    @Operation(summary = "AI 执行任务", description = "调用 Spring AI 执行任务，成功后自动写入任务结果并完成任务")
    @PostMapping("/{teamId}/tasks/{taskId}/run")
    public Map<String, String> runTask(@PathVariable String teamId, @PathVariable String taskId, @RequestBody(required = false) RunTaskRequest request) {
        String teammateId = request == null ? null : request.getTeammateId();
        String output = agentTaskRunnerService.runTask(teamId, taskId, teammateId);
        return Map.of("taskId", taskId, "output", output);
    }
}

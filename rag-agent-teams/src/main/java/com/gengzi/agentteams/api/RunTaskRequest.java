package com.gengzi.agentteams.api;

import io.swagger.v3.oas.annotations.media.Schema;

public class RunTaskRequest {

    @Schema(description = "可选：指定执行任务的成员ID；不传则使用任务 assignee", example = "2ef75da3-2a92-4eb8-a5f7-c37cb13db341")
    private String teammateId;

    public String getTeammateId() {
        return teammateId;
    }

    public void setTeammateId(String teammateId) {
        this.teammateId = teammateId;
    }
}

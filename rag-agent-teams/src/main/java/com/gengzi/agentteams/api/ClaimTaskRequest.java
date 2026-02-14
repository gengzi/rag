package com.gengzi.agentteams.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public class ClaimTaskRequest {

    @NotBlank
    @Schema(description = "认领任务的成员ID", example = "2ef75da3-2a92-4eb8-a5f7-c37cb13db341", requiredMode = Schema.RequiredMode.REQUIRED)
    private String teammateId;

    public String getTeammateId() {
        return teammateId;
    }

    public void setTeammateId(String teammateId) {
        this.teammateId = teammateId;
    }
}

package com.gengzi.agentteams.api;

import jakarta.validation.constraints.NotBlank;

public class ClaimTaskRequest {

    @NotBlank
    private String teammateId;

    public String getTeammateId() {
        return teammateId;
    }

    public void setTeammateId(String teammateId) {
        this.teammateId = teammateId;
    }
}

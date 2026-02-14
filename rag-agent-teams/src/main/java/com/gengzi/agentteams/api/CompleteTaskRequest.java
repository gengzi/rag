package com.gengzi.agentteams.api;

import jakarta.validation.constraints.NotBlank;

public class CompleteTaskRequest {

    @NotBlank
    private String teammateId;

    @NotBlank
    private String result;

    public String getTeammateId() {
        return teammateId;
    }

    public void setTeammateId(String teammateId) {
        this.teammateId = teammateId;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}

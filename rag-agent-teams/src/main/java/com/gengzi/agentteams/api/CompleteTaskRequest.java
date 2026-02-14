package com.gengzi.agentteams.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public class CompleteTaskRequest {

    @NotBlank
    @Schema(description = "完成任务的成员ID（必须是 assignee）", example = "2ef75da3-2a92-4eb8-a5f7-c37cb13db341", requiredMode = Schema.RequiredMode.REQUIRED)
    private String teammateId;

    @NotBlank
    @Schema(description = "任务结果内容（文本）", example = "已完成竞品分析：1) LangSmith ...", requiredMode = Schema.RequiredMode.REQUIRED)
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

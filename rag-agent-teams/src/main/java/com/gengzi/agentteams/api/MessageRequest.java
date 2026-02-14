package com.gengzi.agentteams.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public class MessageRequest {

    @NotBlank
    @Schema(description = "发送人成员ID", example = "2ef75da3-2a92-4eb8-a5f7-c37cb13db341", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fromId;

    @NotBlank
    @Schema(description = "接收人成员ID", example = "7b8328d6-e14f-4d6e-a56c-cf1d97d57a39", requiredMode = Schema.RequiredMode.REQUIRED)
    private String toId;

    @NotBlank
    @Schema(description = "消息内容", example = "我已经完成竞品列表，请基于此做 SWOT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    public String getFromId() {
        return fromId;
    }

    public void setFromId(String fromId) {
        this.fromId = fromId;
    }

    public String getToId() {
        return toId;
    }

    public void setToId(String toId) {
        this.toId = toId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

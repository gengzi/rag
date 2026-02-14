package com.gengzi.agentteams.api;

import jakarta.validation.constraints.NotBlank;

public class MessageRequest {

    @NotBlank
    private String fromId;

    @NotBlank
    private String toId;

    @NotBlank
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

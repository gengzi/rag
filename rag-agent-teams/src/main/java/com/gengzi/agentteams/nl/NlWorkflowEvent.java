package com.gengzi.agentteams.nl;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "实时工作流事件")
public class NlWorkflowEvent {

    @Schema(description = "事件类型", example = "ACTION_START")
    private String type;

    @Schema(description = "事件描述", example = "开始执行动作 RUN_TASK")
    private String message;

    @Schema(description = "事件时间", example = "2026-02-14T06:01:30.000Z")
    private Instant timestamp;

    @Schema(description = "可选扩展数据")
    private Object data;

    public static NlWorkflowEvent of(String type, String message, Object data) {
        NlWorkflowEvent event = new NlWorkflowEvent();
        event.setType(type);
        event.setMessage(message);
        event.setTimestamp(Instant.now());
        event.setData(data);
        return event;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}

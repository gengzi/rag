package com.gengzi.agentteams.nl;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "自然语言对话请求")
public class NlChatRequest {

    @Schema(description = "会话ID。首次可不传，服务端会自动创建并返回", example = "2f8c3c28-01ba-4f31-a7f5-6c8f7df0b1b0")
    private String sessionId;

    @Schema(description = "团队ID。可不传，服务会优先使用会话中最近一次团队", example = "5dbd90ef-1d42-4928-af76-6f0c0734ca00")
    private String teamId;

    @NotBlank
    @Schema(description = "用户自然语言指令/对话内容", example = "创建一个市场分析团队，包含 Alice(Researcher) 和 Bob(Analyst)，然后给 Alice 创建一个收集竞品任务并执行")
    private String message;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

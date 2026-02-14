package com.gengzi.agentteams.nl;

import com.gengzi.agentteams.api.TeamStateResponse;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "自然语言执行结果")
public class NlChatResponse {

    @Schema(description = "会话ID", example = "2f8c3c28-01ba-4f31-a7f5-6c8f7df0b1b0")
    private String sessionId;

    @Schema(description = "当前团队ID", example = "5dbd90ef-1d42-4928-af76-6f0c0734ca00")
    private String teamId;

    @Schema(description = "助手回复", example = "团队已创建，并已完成第一个任务。")
    private String assistantReply;

    @Schema(description = "团队最新状态")
    private TeamStateResponse state;

    @ArraySchema(arraySchema = @Schema(description = "本次执行的事件日志"))
    private List<NlWorkflowEvent> events = new ArrayList<>();

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

    public String getAssistantReply() {
        return assistantReply;
    }

    public void setAssistantReply(String assistantReply) {
        this.assistantReply = assistantReply;
    }

    public TeamStateResponse getState() {
        return state;
    }

    public void setState(TeamStateResponse state) {
        this.state = state;
    }

    public List<NlWorkflowEvent> getEvents() {
        return events;
    }

    public void setEvents(List<NlWorkflowEvent> events) {
        this.events = events;
    }
}

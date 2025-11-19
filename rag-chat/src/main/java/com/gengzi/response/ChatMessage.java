package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Data
public class ChatMessage {
    private List<ChatMessageResponse> content;
    private String id;
    private String role;
    private String conversationId;
    private long createdAt;
}

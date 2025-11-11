package com.gengzi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gengzi.response.ChatAnswerResponse;
import com.gengzi.response.RagReference;
import lombok.Data;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY) // 关键注解
@Data
public class RagChatMessage {
    private List<ChatAnswerResponse> content;
    private String id;
    private String role;
    private String conversationId;
    private long createdAt;


}

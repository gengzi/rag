package com.gengzi.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_EMPTY) // 关键注解
@Data
public class RagChatMessage {
    private String content;
    private String id;
    private String role;
    private String conversationId;
    private RagReference ragReference;
    private String answer;
    private long createdAt;
    private String prompt;

}

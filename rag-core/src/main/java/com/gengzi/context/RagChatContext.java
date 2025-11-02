package com.gengzi.context;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RagChatContext {

    public static final String RAG_CHAT_CONTEXT = "ragChatContext";

    public static final String CHAT_ID = "chatId";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String USER_ID = "userId";


    /**
     * 对话id（某用户的某一次提问和回答）
     */
    private String chatId;

    /**
     * 会话id（某用户）
     */
    private String conversationId;

    /**
     * 用户id
     */
    private String userId;


}

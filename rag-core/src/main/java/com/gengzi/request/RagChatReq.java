package com.gengzi.request;

import lombok.Data;

@Data
public class RagChatReq {

    /**
     * 问题
     */
    private String question;

    /**
     * 会话id
     */
    private String conversationId;


}

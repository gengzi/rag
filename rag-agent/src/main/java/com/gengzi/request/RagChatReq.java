package com.gengzi.request;

import lombok.Data;

@Data
public class RagChatReq {

    /**
     * 用户输入
     */
    private String question;

    /**
     * 会话id  标记整个会话
     */
    private String conversationId;


    /**
     * 标记一次连续的对话信息 （agent 连续操作的对话信息流（比如人类反馈））
     */
    private String threadId;


    /**
     * 本次要使用的agentid
     */
    private String agentId;

    private String userId;


}

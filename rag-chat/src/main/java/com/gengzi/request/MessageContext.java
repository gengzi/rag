package com.gengzi.request;


import lombok.Data;

/**
 * 用于在聊天对话前将聊天记录暂存储起来，方便后续缓存chunk 和 续传
 */
@Data
public class MessageContext {


    /**
     * ai回复的messageid
     */
    private String messageId;

}

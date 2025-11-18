package com.gengzi.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 聊天对话请求类
 */
@Data
public class ChatReq {
    /**
     * 会话id
     */
    @NotBlank(message = "会话id不能为空")
    private String conversationId;

    /**
     * 用户输入内容
     */
    @NotBlank(message = "用户输入内容不能为空")
    private String query;

    /**
     * 标记一次连续的agengt对话信息关联id
     * 取值前端传参根据每次响应结果中的threadId
     */
    private String threadId;

    /**
     * 关联使用的agent对象的id
     * 不使用agent能力，不传此参数
     */
    private String agentId;

}

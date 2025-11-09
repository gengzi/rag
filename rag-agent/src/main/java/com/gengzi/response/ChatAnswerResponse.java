package com.gengzi.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatAnswerResponse {

    /**
     * 存放实际的响应内容
     * 普通消息
     * agent消息
     *
     *
     */
    private Object content;

    /**
     * agent_response
     * llm_response
     */
    private String messageType;



}

package com.gengzi.response;

import com.gengzi.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {


//    // "user", "assistant"
//    private String role;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 存放实际的响应内容
     * 普通消息
     * agent消息
     */
    private Object content;

    /**
     * agent_response agent
     * llm_response  text
     */
    private String messageType;

    // 构造 LLM 消息
    public static ChatMessageResponse ofLlm(LlmTextRes llmTextRes) {
        ChatMessageResponse msg = new ChatMessageResponse();
        msg.messageType = MessageType.LLM_RESPONSE.getTypeCode();
        msg.content = llmTextRes;
        return msg;
    }

    // 构造 Agent 消息
    public static ChatMessageResponse ofAgent(AgentGraphRes agentGraphRes) {
        ChatMessageResponse msg = new ChatMessageResponse();
        msg.messageType = MessageType.AGENT_RESPONSE.getTypeCode();
        msg.content = agentGraphRes;
        return msg;
    }


}

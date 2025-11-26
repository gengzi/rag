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

//    /**
//     * 时间戳
//     */
//    private Long timestamp;

    /**
     * 标识一次agent节点的执行关联线程id
     */
    private String threadId;

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


    /**
     * 在流返回时，此字段代表顺序号
     */
    private Long seqNum;

    /**
     * 消息id 在流返回时，此字段代表一条消息
     */
    private String messageId;

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

    // web 视图
    public static ChatMessageResponse ofWebView(WebViewRes webViewRes) {
        ChatMessageResponse msg = new ChatMessageResponse();
        msg.messageType = MessageType.WEB_VIEW.getTypeCode();
        msg.content = webViewRes;
        return msg;
    }

    // end 结束
    public static ChatMessageResponse ofEnd() {
        ChatMessageResponse msg = new ChatMessageResponse();
        msg.messageType = MessageType.END_OF_STREAM.getTypeCode();
        msg.content = "";
        return msg;
    }

    // 正在对话中，请稍后
    public static ChatMessageResponse ofRlocking() {
        ChatMessageResponse msg = new ChatMessageResponse();
        msg.messageType = MessageType.RLOCK.getTypeCode();
        msg.content = "正在对话中，请刷新页面";
        return msg;
    }


}

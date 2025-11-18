package com.gengzi.service;

import com.gengzi.request.ChatReq;
import com.gengzi.response.ChatMessageResponse;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface ChatService {


    /**
     * 流式返回大模型的回复
     * 支持agent节点的流式输出
     * 支持普通对话的流式输出（默认走rag流程） 可以增加意图识别，来判断是否走rag流程
     * @param req
     * @return
     */
    Flux<ServerSentEvent<ChatMessageResponse>> chatRag(ChatReq req);

    Object chatRagMsgList(String conversationId);

}

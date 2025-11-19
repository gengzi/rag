package com.gengzi.service;

import com.gengzi.request.ChatMsgRecordReq;
import com.gengzi.request.ChatReq;
import com.gengzi.response.ChatMessageResponse;
import com.gengzi.response.ConversationDetailsResponse;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ChatService {


    /**
     * 流式返回大模型的回复
     * 支持agent节点的流式输出
     * 支持普通对话的流式输出（默认走rag流程） 可以增加意图识别，来判断是否走rag流程
     * @param req
     * @return
     */
    Flux<ServerSentEvent<ChatMessageResponse>> chat(ChatReq req);

    /**
     * 获取聊天记录，分页获取
     * @param conversationId
     * @param recordReq
     * @return
     */
    Mono<ConversationDetailsResponse> chatMsgList(String conversationId, ChatMsgRecordReq recordReq);

}

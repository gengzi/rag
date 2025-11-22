package com.gengzi.rag.search.service;

import com.gengzi.request.ChatReq;
import com.gengzi.response.ChatMessageResponse;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

public interface ChatRagService {


    /**
     * rag对话
     *
     * @param ragChatReq rag对话参数
     * @param userId     用户id
     * @return
     */
    Flux<ChatMessageResponse> chatRag(ChatReq ragChatReq, String userId);


    /**
     * agent节点调用
     *
     * @param ragChatReq
     * @param userId
     * @return
     */
    Flux<ChatResponse> chatRagByAgent(ChatReq ragChatReq, String userId);


}

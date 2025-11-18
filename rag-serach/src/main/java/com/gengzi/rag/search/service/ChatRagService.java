package com.gengzi.rag.search.service;

import com.gengzi.request.ChatReq;
import com.gengzi.response.ChatMessageResponse;
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


}

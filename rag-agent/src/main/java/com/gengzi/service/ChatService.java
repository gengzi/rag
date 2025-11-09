package com.gengzi.service;

import com.gengzi.request.AiPPTGenerateReq;
import com.gengzi.request.RagChatReq;
import com.gengzi.response.ChatAnswerResponse;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface ChatService {


    Flux<ServerSentEvent<ChatAnswerResponse>> chatRag(RagChatReq req);

}

package com.gengzi.service;


import com.gengzi.request.ChatReq;
import com.gengzi.response.ChatMessageResponse;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface DeepResearchService {

    Flux<ServerSentEvent<ChatMessageResponse>> deepResearch(ChatReq req);


}

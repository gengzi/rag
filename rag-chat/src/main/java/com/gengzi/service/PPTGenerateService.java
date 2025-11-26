package com.gengzi.service;


import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.gengzi.request.ChatReq;
import com.gengzi.response.ChatMessageResponse;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface PPTGenerateService {

    Flux<ServerSentEvent<ChatMessageResponse>> pptGenerate(ChatReq req) throws GraphRunnerException;


}

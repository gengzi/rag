package com.gengzi.ragagent.service;

import com.gengzi.ragagent.request.RagChatReq;
import com.gengzi.response.ChatMessageResponse;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface DeepResearchService {

    Flux<ServerSentEvent<ChatMessageResponse>> deepResearch(RagChatReq req);


}

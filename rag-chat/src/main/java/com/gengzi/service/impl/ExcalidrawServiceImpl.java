package com.gengzi.service.impl;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.gengzi.rag.agent.excalidaw.ExcalidrawAgent;
import com.gengzi.request.ChatReq;
import com.gengzi.response.ChatMessageResponse;
import com.gengzi.service.ExcalidrawService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ExcalidrawServiceImpl implements ExcalidrawService {


    @Autowired
    private ExcalidrawAgent excalidrawAgent;

    /**
     * @param req
     * @return
     * @throws GraphRunnerException
     */
    @Override
    public Flux<ServerSentEvent<ChatMessageResponse>> excalidrawGenerate(ChatReq req) throws GraphRunnerException {
        return excalidrawAgent.excalidrawGenerate(req);
    }
}

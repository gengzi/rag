package com.gengzi.service.impl;


import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.constant.SaverEnum;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.gengzi.rag.agent.deepresearch.process.DeepResearchGraphProcess;
import com.gengzi.request.ChatReq;
import com.gengzi.response.ChatMessageResponse;
import com.gengzi.service.DeepResearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;

@Service
public class DeepResearchServiceImpl implements DeepResearchService {
    private static final Logger logger = LoggerFactory.getLogger(DeepResearchServiceImpl.class);

    private MemorySaver memorySaver;

    private CompiledGraph compiledGraph;

    @Autowired
    private DeepResearchGraphProcess deepResearchGraphProcess;

    public DeepResearchServiceImpl(@Qualifier("deepResearch") StateGraph deepResearch) throws GraphStateException {
        // 记忆缓存类
        memorySaver = new MemorySaver();
        SaverConfig saverConfig = SaverConfig.builder().register(SaverEnum.MEMORY.getValue(), memorySaver).build();
        this.compiledGraph = deepResearch.compile(CompileConfig.builder().saverConfig(saverConfig).build());
    }

    /**
     * @param req
     * @return
     */
    @Override
    public Flux<ServerSentEvent<ChatMessageResponse>> deepResearch(ChatReq req) {
        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put("query", req.getQuery());
        objectMap.put("conversationId", req.getConversationId());
        String threadId = deepResearchGraphProcess.createSession(req.getConversationId());
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(threadId).build();
        // 输出
        Sinks.Many<ServerSentEvent<ChatMessageResponse>> sink = Sinks.many().unicast().onBackpressureBuffer();
        Flux<NodeOutput> nodeOutputFlux = compiledGraph.fluxStream(objectMap, runnableConfig);
        deepResearchGraphProcess.processStream(threadId, nodeOutputFlux, sink);

        return sink.asFlux()
                .doOnCancel(() -> logger.info("Client disconnected from stream"))
                .doOnError(e -> logger.error("Error occurred during streaming", e));
    }


}

package com.gengzi.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.gengzi.graph.GraphProcess;
import com.gengzi.request.AiPPTGenerateReq;
import com.gengzi.service.AiPPTService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/aippt")
public class AiPPTController {

    private static final Logger logger = LoggerFactory.getLogger(AiPPTController.class);
    private final CompiledGraph compile;
    @Autowired
    private AiPPTService aiPPTService;

    public AiPPTController(@Qualifier("streamGraph") StateGraph stateGraph) throws GraphStateException {
        this.compile = stateGraph.compile();
    }


    /**
     * 生成
     *
     * @return
     * @throws GraphRunnerException
     */
    @PostMapping(value = "/generate")
    public void expand(@RequestBody AiPPTGenerateReq req) throws Exception {
        aiPPTService.generatePPT(req);
    }


    @PostMapping(value = "/generateStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> generateStream(@RequestBody AiPPTGenerateReq req) {
        String threadId = Thread.currentThread().getName();
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(threadId).build();
        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put("query", req.getQuery());
        GraphProcess graphProcess = new GraphProcess(compile);
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        Flux<NodeOutput> nodeOutputFlux = compile.fluxStream(objectMap, runnableConfig);
        graphProcess.processStream(nodeOutputFlux, sink);

        return sink.asFlux()
                .doOnCancel(() -> logger.info("Client disconnected from stream"))
                .doOnError(e -> logger.error("Error occurred during streaming", e));
    }


}
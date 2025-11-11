package com.gengzi.controller;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.constant.SaverEnum;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.gengzi.graph.GraphProcess;
import com.gengzi.request.AiPPTGenerateReq;
import com.gengzi.request.RagChatReq;
import com.gengzi.response.ChatAnswerResponse;
import com.gengzi.response.Result;
import com.gengzi.service.AiPPTService;
import com.gengzi.service.ChatService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/aippt")
@Tag(name = "测试", description = "测试")
public class AiPPTController {

    private static final Logger logger = LoggerFactory.getLogger(AiPPTController.class);
    private final CompiledGraph compile;
    @Autowired
    private AiPPTService aiPPTService;

    @Autowired
    private ChatService chatService;

    public AiPPTController(@Qualifier("streamGraph") StateGraph stateGraph) throws GraphStateException {
//        this.compile = stateGraph.compile();
        SaverConfig saverConfig = SaverConfig.builder().register(SaverEnum.MEMORY.getValue(), new MemorySaver()).build();
//        this.compile = stateGraph
//                .compile(CompileConfig.builder().saverConfig(saverConfig).interruptBefore("humanFeedbackNode").build());
        this.compile = stateGraph
                .compile(CompileConfig.builder().saverConfig(saverConfig).build());
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


    /**
     * 通过graph图形式，根据用户问题生成ppt
     *
     * @param req
     * @return
     */
    @PostMapping(value = "/generateStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> generateStream(@RequestBody AiPPTGenerateReq req) {
        // 运行配置
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(req.getSessionId()).build();
        // 入参
        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put("query", req.getQuery());
        GraphProcess graphProcess = new GraphProcess(compile);
        // 输出
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        Flux<NodeOutput> nodeOutputFlux = compile.fluxStream(objectMap, runnableConfig);
        graphProcess.processStream(nodeOutputFlux, sink);

        return sink.asFlux()
                .doOnCancel(() -> logger.info("Client disconnected from stream"))
                .doOnError(e -> logger.error("Error occurred during streaming", e));
    }


    @PostMapping(value = "/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> resume(@RequestBody AiPPTGenerateReq req) throws GraphRunnerException {
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(req.getSessionId()).build();
        StateSnapshot stateSnapshot = this.compile.getState(runnableConfig);
        OverAllState state = stateSnapshot.state();
        state.withResume();

        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put("feedback", req.getQuery());

        state.withHumanFeedback(new OverAllState.HumanFeedback(objectMap, ""));

        // Create a unicast sink to emit ServerSentEvents
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        GraphProcess graphProcess = new GraphProcess(this.compile);
        Flux<NodeOutput> resultFuture = compile.fluxStreamFromInitialNode(state, runnableConfig);
        graphProcess.processStream(resultFuture, sink);

        return sink.asFlux()
                .doOnCancel(() -> logger.info("Client disconnected from stream"))
                .doOnError(e -> logger.error("Error occurred during streaming", e));
    }





    @PostMapping(value = "/chat/rag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatAnswerResponse>> chatRag(@RequestBody RagChatReq req) {
        return chatService.chatRag(req);
    }

    @GetMapping("/chat/rag/msg/list")
    public Result<?> chatRagMsgList(@RequestParam String conversationId) {
        return Result.success(chatService.chatRagMsgList(conversationId));
    }

}
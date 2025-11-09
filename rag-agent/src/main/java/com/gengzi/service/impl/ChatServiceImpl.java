package com.gengzi.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.constant.SaverEnum;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.gengzi.graph.TestGraphProcess;
import com.gengzi.request.RagChatReq;
import com.gengzi.response.ChatAnswerResponse;
import com.gengzi.response.LlmTextRes;
import com.gengzi.response.RagReference;
import com.gengzi.response.ReferenceDocument;
import com.gengzi.service.ChatService;
import com.gengzi.tool.ppt.config.AiPPTConfig;
import com.gengzi.tool.ppt.generate.AiPPTContentGenerationService;
import com.gengzi.tool.ppt.generate.PptGenerationService;
import com.gengzi.tool.ppt.parser.PptMasterParser;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);
    private final CompiledGraph compile;
    @Autowired
    private PptMasterParser pptMasterParser;
    @Autowired
    private PptGenerationService pptGenerationService;
    @Autowired
    @Qualifier("deepseekChatClientNoRag")
    private ChatClient chatClient;
    @Autowired
    private AiPPTConfig aiPPTConfig;
    @Autowired
    private AiPPTContentGenerationService aiPPTContentGenerationService;

    private MemorySaver memorySaver;

    public ChatServiceImpl(@Qualifier("streamGraph") StateGraph stateGraph) throws GraphStateException {
        memorySaver = new MemorySaver();
        SaverConfig saverConfig = SaverConfig.builder().register(SaverEnum.MEMORY.getValue(), memorySaver).build();
//        this.compile = stateGraph
//                .compile(CompileConfig.builder().saverConfig(saverConfig).build());
        this.compile = stateGraph
                .compile(CompileConfig.builder().saverConfig(saverConfig).interruptBefore("humanFeedbackNode").build());
    }


    /**
     * 需要处理无agent 对话情况
     * 有agent 对话情况
     *
     * @param req
     * @return
     */
    @Override
    public Flux<ServerSentEvent<ChatAnswerResponse>> chatRag(RagChatReq req) {
        Sinks.Many<ServerSentEvent<ChatAnswerResponse>> sink = Sinks.many().unicast().onBackpressureBuffer();
        // 判断本次对话是否有agent 参与
        String agentId = req.getAgentId();
        String conversationId = req.getConversationId();
        if (StrUtil.isNotBlank(agentId)) {
            // 存在agengt 对话
            // 根据agentId 获取对用的agent 图执行方法
            return generateStream(req);
        } else {
            // 不存在agent对话
            return getServerSentEventFlux(req, conversationId, sink);
        }

    }

    public Flux<ServerSentEvent<ChatAnswerResponse>> generateStream(RagChatReq req) {
        // 运行配置
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(req.getSessionId()).build();
        Optional<Checkpoint> checkpoint = memorySaver.get(runnableConfig);
        logger.debug("checkpoint: {}" ,checkpoint);

        try {
            StateSnapshot stateSnapshot = this.compile.getState(runnableConfig);
            // TODO 需要判断下一个节点是 人类反馈节点才进入
            if (stateSnapshot != null && !stateSnapshot.next().equals(StateGraph.END)) {
                try {
                    return resume(req);
                } catch (GraphRunnerException e) {
                    throw new RuntimeException(e);
                }
            }

        } catch (Exception e) {

        }
        // 入参
        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put("query", req.getQuestion());
        TestGraphProcess graphProcess = new TestGraphProcess(compile);
        // 输出
        Sinks.Many<ServerSentEvent<ChatAnswerResponse>> sink = Sinks.many().unicast().onBackpressureBuffer();
        Flux<NodeOutput> nodeOutputFlux = compile.fluxStream(objectMap, runnableConfig);
        graphProcess.processStream(nodeOutputFlux, sink);

        return sink.asFlux()
                .doOnCancel(() -> logger.info("Client disconnected from stream"))
                .doOnError(e -> logger.error("Error occurred during streaming", e));
    }


    public Flux<ServerSentEvent<ChatAnswerResponse>> resume(RagChatReq req) throws GraphRunnerException {
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(req.getSessionId()).build();
        StateSnapshot stateSnapshot = this.compile.getState(runnableConfig);
        OverAllState state = stateSnapshot.state();
        state.withResume();

        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put("feedback", req.getQuestion());

        state.withHumanFeedback(new OverAllState.HumanFeedback(objectMap, ""));

        // Create a unicast sink to emit ServerSentEvents
        Sinks.Many<ServerSentEvent<ChatAnswerResponse>> sink = Sinks.many().unicast().onBackpressureBuffer();
        TestGraphProcess graphProcess = new TestGraphProcess(this.compile);
        Flux<NodeOutput> resultFuture = compile.fluxStreamFromInitialNode(state, runnableConfig);
        graphProcess.processStream(resultFuture, sink);

        return sink.asFlux()
                .doOnCancel(() -> logger.info("Client disconnected from stream"))
                .doOnError(e -> logger.error("Error occurred during streaming", e));
    }

    @NotNull
    private Flux<ServerSentEvent<ChatAnswerResponse>> getServerSentEventFlux(RagChatReq req, String conversationId, Sinks.Many<ServerSentEvent<ChatAnswerResponse>> sink) {
        String chatId = IdUtil.simpleUUID();
        Flux<ChatClientResponse> chatClientResponseFlux = chatClient.prompt()
                .user(req.getQuestion())
                .system("你是一个多功能助手，帮助用户解答问题")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .chatClientResponse();
        ChatAnswerResponse done = new ChatAnswerResponse();
        LlmTextRes llmTextRes = new LlmTextRes();
        llmTextRes.setAnswer("[DONE]");
        done.setContent(llmTextRes);
        done.setMessageType("text");


        chatClientResponseFlux.index()
                .doOnNext(result -> {
                    long sequenceNumber = result.getT1() + 1;
                    ChatClientResponse chatClientResponse = result.getT2();
                    ChatAnswerResponse chatAnswerResponse = new ChatAnswerResponse();
                    ChatResponse chatResponse = chatClientResponse.chatResponse();
                    LlmTextRes llmTextRes1 = new LlmTextRes();
                    llmTextRes1.setAnswer(chatResponse.getResult().getOutput().getText());

                    ArrayList<ReferenceDocument> referenceDocuments = new ArrayList<>();
                    ReferenceDocument referenceDocument = new ReferenceDocument();
                    referenceDocument.setDocumentName("参考文档");
                    referenceDocument.setDocumentUrl("https://www.baidu.com");
                    referenceDocument.setDocumentId("1");
                    referenceDocument.setChunkId("1");
                    referenceDocument.setText("参考文档内容");
                    referenceDocument.setScore("1");
                    referenceDocument.setPageRange("1");
                    referenceDocument.setContentType("text");
                    referenceDocuments.add(referenceDocument);

                    RagReference ragReference = new RagReference(chatId, referenceDocuments);
                    llmTextRes1.setReference(ragReference);
                    llmTextRes.setReference(ragReference);
                    chatAnswerResponse.setContent(llmTextRes1);
                    chatAnswerResponse.setMessageType("text");
                    // 返回用户问题后，还需要拼接上参考的文档信息，文档链接
                    sink.tryEmitNext(ServerSentEvent.builder(chatAnswerResponse).build());
                }).doOnComplete(() -> {
                    sink.tryEmitNext(ServerSentEvent.builder(done).build());
                    sink.tryEmitComplete();
                })
                .subscribe();

        return sink.asFlux()
                .doOnCancel(() -> logger.info("Client disconnected from stream"))
                .doOnError(e -> logger.error("Error occurred during streaming", e));
    }
}

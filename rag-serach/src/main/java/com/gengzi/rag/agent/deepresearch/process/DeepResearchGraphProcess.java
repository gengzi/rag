package com.gengzi.rag.agent.deepresearch.process;


import cn.hutool.core.util.IdUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.gengzi.rag.agent.deepresearch.config.DeepResearchConfig;
import com.gengzi.response.AgentGraphRes;
import com.gengzi.response.ChatMessageResponse;
import com.gengzi.response.LlmTextRes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Service
public class DeepResearchGraphProcess {
    private static final Logger logger = LoggerFactory.getLogger(DeepResearchGraphProcess.class);

    @Autowired
    private DeepResearchConfig deepResearchConfig;

    public String createSession(String conversationId) {
        return String.format("%s_%s", conversationId, IdUtil.simpleUUID());
    }

    public void processStream(String threadId, Flux<NodeOutput> nodeOutputFlux, Sinks.Many<ServerSentEvent<ChatMessageResponse>> sink) {

        Mono.fromRunnable(() -> {
                            nodeOutputFlux.doOnNext(
                                            nodeOutput -> {
                                                logger.info("Received node output : {} output node name: {}", nodeOutput, nodeOutput.node());
                                                if(nodeOutput.isSTART() || nodeOutput.isEND()){
                                                    return;
                                                }
                                                // 两种类型输出，一种是流示输出，一种是总结输出
                                                // TODO TEST
                                                if (nodeOutput.state().data().get("deep_research") != null) {
                                                    AgentGraphRes agentGraphRes = new AgentGraphRes();
                                                    agentGraphRes.setNodeName(nodeOutput.node());
                                                    agentGraphRes.setContent("### 开始检索...");
                                                    agentGraphRes.setDisplayTitle(deepResearchConfig.getDeepresearchNodes().get(nodeOutput.node()).getDisplayTitle());
                                                    ChatMessageResponse chatMessageResponse = ChatMessageResponse.ofAgent(agentGraphRes);
                                                    chatMessageResponse.setThreadId(threadId);
                                                    sink.tryEmitNext(ServerSentEvent.builder(chatMessageResponse).build());
                                                } else {
                                                    String answer = (String) nodeOutput.state().data().get("output");
                                                    LlmTextRes llmTextRes = new LlmTextRes();
                                                    llmTextRes.setAnswer(answer);
                                                    ChatMessageResponse chatMessageResponse = ChatMessageResponse.ofLlm(llmTextRes);
                                                    chatMessageResponse.setThreadId(threadId);
                                                    sink.tryEmitNext(ServerSentEvent.builder(chatMessageResponse).build());
                                                }
                                            }
                                    )
                                    .doOnError(e -> {
                                        logger.error("Error occurred during streaming", e);
                                        sink.tryEmitError(e);
                                    })
                                    .doOnComplete(
                                            () -> {
                                                logger.info("Streaming completed");
                                                sink.tryEmitComplete();
                                            }
                                    )
                                    .subscribe();
                        }
                ).subscribeOn(Schedulers.boundedElastic())
                .subscribe();


    }
}

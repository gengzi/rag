package com.gengzi.rag.agent.deepresearch.process;


import cn.hutool.core.util.IdUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
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

import java.util.List;
import java.util.stream.Collectors;

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
                                                if (nodeOutput.isSTART() || nodeOutput.isEND()) {
                                                    // 针对开始节点和结束节点，不做内容输出
                                                    return;
                                                }
                                                // 两种类型输出，一种是流示输出，一种是总结输出
                                                // 流示和总结也分两种一种是agent，一种是text文本


                                                // 针对特点节点，需要做单独处理

                                                switch (nodeOutput.node()) {
                                                    case "CoordinatorNode":
                                                        coordinatorNodeOutput(nodeOutput, threadId, sink);
                                                        break;
                                                    case "RewriteAndMultiQueryNode":
                                                        rewriteAndMultiQueryNodeOutput(nodeOutput, threadId, sink);
                                                        break;
                                                    case "BackgroundInvectigationNode":
                                                        backgroundInvectigationNodeOutput(nodeOutput, threadId, sink);
                                                        break;

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

    private void backgroundInvectigationNodeOutput(NodeOutput nodeOutput, String threadId, Sinks.Many<ServerSentEvent<ChatMessageResponse>> sink) {
        // 流示输出

        AgentGraphRes agentGraphRes = new AgentGraphRes();
        if (nodeOutput instanceof StreamingOutput streamingOutput) {
            logger.info("agent streamingOutput = {}", streamingOutput);
            agentGraphRes.setContent(streamingOutput.chunk());
        }
        agentGraphRes.setNodeName(nodeOutput.node());
        // nodename 对应的标题名称
        agentGraphRes.setDisplayTitle(deepResearchConfig.getDeepresearchNodes().get(nodeOutput.node()).getDisplayTitle());
        ChatMessageResponse chatMessageResponse = ChatMessageResponse.ofAgent(agentGraphRes);
        chatMessageResponse.setThreadId(threadId);
        sink.tryEmitNext(ServerSentEvent.builder(chatMessageResponse).build());
    }

    private void rewriteAndMultiQueryNodeOutput(NodeOutput nodeOutput, String threadId, Sinks.Many<ServerSentEvent<ChatMessageResponse>> sink) {
        List<String> answer = (List<String>) nodeOutput.state().data().get("optimize_queries");
        AgentGraphRes agentGraphRes = new AgentGraphRes();
        agentGraphRes.setNodeName(nodeOutput.node());
        agentGraphRes.setContent(answer.stream().collect(Collectors.joining("\n\n")));
        agentGraphRes.setDisplayTitle(deepResearchConfig.getDeepresearchNodes().get(nodeOutput.node()).getDisplayTitle());
        ChatMessageResponse chatMessageResponse = ChatMessageResponse.ofAgent(agentGraphRes);
        chatMessageResponse.setThreadId(threadId);
        sink.tryEmitNext(ServerSentEvent.builder(chatMessageResponse).build());
    }

    private void coordinatorNodeOutput(NodeOutput nodeOutput, String threadId, Sinks.Many<ServerSentEvent<ChatMessageResponse>> sink) {
        if (nodeOutput.state().data().get("deep_research") != null) {
            AgentGraphRes agentGraphRes = new AgentGraphRes();
            agentGraphRes.setNodeName(nodeOutput.node());
            agentGraphRes.setContent("## 意图识别完成，下一步进行问题重写");
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
}

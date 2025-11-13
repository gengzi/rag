package com.gengzi.graph;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.gengzi.enums.NodeType;
import com.gengzi.enums.ParticipantType;
import com.gengzi.response.AgentGraphRes;
import com.gengzi.response.ChatAnswerResponse;
import com.gengzi.response.LlmTextRes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * 比如定义 nodeName 后缀 TextStream 流的，就代表文本流示输出  Text 代表整个文本输出
 * <p>
 * nodeName 后缀 agentStream 就代表节点流示输出  agent代表ageng节点整个输出
 */
public class TestGraphProcess {

    private static final Logger logger = LoggerFactory.getLogger(TestGraphProcess.class);

    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    private final Scheduler scheduler = Schedulers.boundedElastic();
    private final ConcurrentHashMap<String, Integer> sessionCountMap = new ConcurrentHashMap<>();
    private CompiledGraph compiledGraph;

    public TestGraphProcess(CompiledGraph compiledGraph) {
        this.compiledGraph = compiledGraph;
    }


    public String createSession(String conversationId) {
//        Integer merge = sessionCountMap.merge(conversationId, 1, Integer::sum);
        return String.format("%s_%s", conversationId, IdUtil.simpleUUID());
    }


    /**
     * 从大模型中获取结果，并结果推送到客户端
     *
     * @param generator
     * @param sink
     */
    public void processStream(String threadId,Flux<NodeOutput> generator, Sinks.Many<ServerSentEvent<ChatAnswerResponse>> sink) {
        Mono.fromRunnable(() -> {
                    generator
                            .doOnNext(output -> {
                                logger.info("output = {}", output);
                                String nodeName = output.node();
                                // start，end 不要展示
                                if (output.isSTART() || output.isEND()) {
                                    return;
                                }
                                // 人类反馈节点也不展示
                                if (NodeType.HUMAN_FEEDBACK_NODE.getCode().equals(nodeName)) {
                                    return;
                                }


                                String content;
//                    if (output instanceof StreamingOutput streamingOutput) {
//                        content = JSON.toJSONString(Map.of(nodeName, streamingOutput.chunk()));
//                    } else {
//                        JSONObject nodeOutput = new JSONObject();
//                        nodeOutput.put("data", output.state().data());
//                        nodeOutput.put("node", nodeName);
//                        content = JSON.toJSONString(nodeOutput);
//                    }

                                ChatAnswerResponse chatAnswerResponse = new ChatAnswerResponse();
                                chatAnswerResponse.setThreadId(threadId);
                                NodeType nodeType = NodeType.fromCode(nodeName);

                                if (nodeType.getOutputNode().endsWith("TextStream")) {
                                    LlmTextRes llmTextRes = new LlmTextRes();
                                    if (output instanceof StreamingOutput streamingOutput) {
                                        llmTextRes.setAnswer(streamingOutput.chunk());
                                    }
                                    chatAnswerResponse.setMessageType("text");
                                    chatAnswerResponse.setContent(llmTextRes);

                                } else if (nodeType.getOutputNode().endsWith("AgentStream")) {
                                    AgentGraphRes agentGraphRes = new AgentGraphRes();
                                    if (output instanceof StreamingOutput streamingOutput) {
                                        logger.info("agent streamingOutput = {}", streamingOutput);
                                        agentGraphRes.setContent(streamingOutput.chunk());
                                    }
                                    agentGraphRes.setNodeName(nodeName);
                                    // nodename 对应的标题名称
                                    agentGraphRes.setDisplayTitle(NodeType.fromCode(nodeName).getDescription());
                                    chatAnswerResponse.setMessageType(ParticipantType.AGENT.getCode());
                                    chatAnswerResponse.setContent(agentGraphRes);

                                } else {
                                    AgentGraphRes agentGraphRes = new AgentGraphRes();
                                    agentGraphRes.setNodeName(nodeName);
                                    agentGraphRes.setContent(JSONUtil.toJsonStr(output.state().data()));
                                    chatAnswerResponse.setMessageType(ParticipantType.AGENT.getCode());
                                    chatAnswerResponse.setContent(agentGraphRes);

                                }
                                sink.tryEmitNext(ServerSentEvent.builder(chatAnswerResponse).build());


                            })
                            .doOnComplete(() -> {
                                // 正常完成
                                sink.tryEmitComplete();
                            })
                            .doOnError(e -> {
                                logger.error("Error occurred during streaming", e);
                                ChatAnswerResponse chatAnswerResponse = new ChatAnswerResponse();
                                chatAnswerResponse.setMessageType(ParticipantType.TEXT.getCode());
                                chatAnswerResponse.setThreadId("");
                                LlmTextRes llmTextRes = new LlmTextRes();
                                llmTextRes.setAnswer("# 系统异常，请稍后~ ");
                                chatAnswerResponse.setContent(llmTextRes);
                                sink.tryEmitNext(ServerSentEvent.builder(chatAnswerResponse).build());
//                                sink.tryEmitError(e);
                                sink.tryEmitComplete();
                            })
                            // 进行流的订阅，才进行流处理
                            .subscribe();
                })
                .subscribeOn(scheduler)
                .subscribe();

//         executor.submit(() -> {
//            logger.info("Before subscribe: = {}", Thread.currentThread().getName());
//            generator
//                    .doOnNext(output -> {
//                        logger.info("output = {}", output);
//                        String nodeName = output.node();
//                        // start，end 不要展示
//                        if (output.isSTART() || output.isEND()) {
//                            return;
//                        }
//                        String content;
////                    if (output instanceof StreamingOutput streamingOutput) {
////                        content = JSON.toJSONString(Map.of(nodeName, streamingOutput.chunk()));
////                    } else {
////                        JSONObject nodeOutput = new JSONObject();
////                        nodeOutput.put("data", output.state().data());
////                        nodeOutput.put("node", nodeName);
////                        content = JSON.toJSONString(nodeOutput);
////                    }
//
//                        ChatAnswerResponse chatAnswerResponse = new ChatAnswerResponse();
//                        NodeType nodeType = NodeType.fromCode(nodeName);
//
//                        if (nodeType.getOutputNode().endsWith("TextStream")) {
//                            LlmTextRes llmTextRes = new LlmTextRes();
//                            if (output instanceof StreamingOutput streamingOutput) {
//                                llmTextRes.setAnswer(streamingOutput.chunk());
//                            }
//                            chatAnswerResponse.setMessageType("text");
//                            chatAnswerResponse.setContent(llmTextRes);
//
//                        } else if (nodeType.getOutputNode().endsWith("AgentStream")) {
//                            AgentGraphRes agentGraphRes = new AgentGraphRes();
//                            if (output instanceof StreamingOutput streamingOutput) {
//                                logger.info("agent streamingOutput = {}", streamingOutput);
//                                agentGraphRes.setContent(streamingOutput.chunk());
//                            }
//                            agentGraphRes.setNodeName(nodeName);
//                            // nodename 对应的标题名称
//                            agentGraphRes.setDisplayTitle(NodeType.fromCode(nodeName).getDescription());
//                            chatAnswerResponse.setMessageType(ParticipantType.AGENT.getCode());
//                            chatAnswerResponse.setContent(agentGraphRes);
//
//                        } else {
//                            AgentGraphRes agentGraphRes = new AgentGraphRes();
//                            agentGraphRes.setNodeName(nodeName);
//                            agentGraphRes.setContent(JSONUtil.toJsonStr(output.state().data()));
//                            chatAnswerResponse.setMessageType(ParticipantType.AGENT.getCode());
//                            chatAnswerResponse.setContent(agentGraphRes);
//
//                        }
//                        sink.tryEmitNext(ServerSentEvent.builder(chatAnswerResponse).build());
//
//
//                    })
//                    .doOnComplete(() -> {
//                        // 正常完成
//                        sink.tryEmitComplete();
//                    })
//                    .doOnError(e -> {
//                        logger.error("Error occurred during streaming", e);
//                        sink.tryEmitError(e);
//                    })
//                    // 进行流的订阅，才进行流处理
//                    .subscribe();
//            logger.info("After subscribe: = {}", Thread.currentThread().getName());
//        });
    }
}
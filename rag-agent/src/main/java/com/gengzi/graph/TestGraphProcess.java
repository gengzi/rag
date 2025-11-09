package com.gengzi.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.gengzi.response.AgentGraphRes;
import com.gengzi.response.AgentInfo;
import com.gengzi.response.ChatAnswerResponse;
import com.gengzi.response.LlmTextRes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;

public class TestGraphProcess {

    private static final Logger logger = LoggerFactory.getLogger(TestGraphProcess.class);


    private CompiledGraph compiledGraph;

    public TestGraphProcess(CompiledGraph compiledGraph) {
        this.compiledGraph = compiledGraph;
    }

    /**
     * 从大模型中获取结果，并结果推送到客户端
     *
     * @param generator
     * @param sink
     */
    public void processStream(Flux<NodeOutput> generator, Sinks.Many<ServerSentEvent<ChatAnswerResponse>> sink) {
        generator
                .doOnNext(output -> {
                    logger.info("output = {}", output);
                    String nodeName = output.node();
                    String content;
                    if (output instanceof StreamingOutput streamingOutput) {
                        content = JSON.toJSONString(Map.of(nodeName, streamingOutput.chunk()));
                    } else {
                        JSONObject nodeOutput = new JSONObject();
                        nodeOutput.put("data", output.state().data());
                        nodeOutput.put("node", nodeName);
                        content = JSON.toJSONString(nodeOutput);
                    }

                    ChatAnswerResponse chatAnswerResponse = new ChatAnswerResponse();



                    if(nodeName.equals("OutlineGenerationNodeStream")){
                        LlmTextRes llmTextRes = new LlmTextRes();
                        if (output instanceof StreamingOutput streamingOutput) {
                            llmTextRes.setAnswer(streamingOutput.chunk());
                        }
                        chatAnswerResponse.setMessageType("text");
                        chatAnswerResponse.setContent(llmTextRes);
                    }else{
                        AgentInfo agentInfo = new AgentInfo();

                        agentInfo.setNodeName(nodeName);
                        agentInfo.setContent(content);
                        agentInfo.setDisplayTitle(nodeName);
                        agentInfo.setSessionId("xx");
                        AgentGraphRes agentGraphRes = new AgentGraphRes();
                        agentGraphRes.setNodeName(nodeName);
//                        agentGraphRes.setType("agent");
                        agentGraphRes.setContent(content);
                        chatAnswerResponse.setMessageType("agent");
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
                    sink.tryEmitError(e);
                })
                .subscribe();
    }
}
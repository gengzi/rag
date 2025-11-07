package com.gengzi.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GraphProcess {

    private static final Logger logger = LoggerFactory.getLogger(GraphProcess.class);


    private CompiledGraph compiledGraph;

    public GraphProcess(CompiledGraph compiledGraph) {
        this.compiledGraph = compiledGraph;
    }

    /**
     * 从大模型中获取结果，并结果推送到客户端
     *
     * @param generator
     * @param sink
     */
    public void processStream(Flux<NodeOutput> generator, Sinks.Many<ServerSentEvent<String>> sink) {
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
                    sink.tryEmitNext(ServerSentEvent.builder(content).build());
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
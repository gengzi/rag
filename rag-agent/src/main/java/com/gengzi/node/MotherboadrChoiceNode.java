package com.gengzi.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.gengzi.tool.ppt.config.AiPPTConfig;
import com.gengzi.util.convert.FluxConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 大纲生成节点
 */
@Component
public class MotherboadrChoiceNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(MotherboadrChoiceNode.class);

    @Autowired
    @Qualifier("deepseekChatClientNoRag")
    private ChatClient chatClient;

    @Override
    public Map<String, Object> apply(OverAllState state) {



        Flux<String> stringFlux = Flux.fromIterable(List.of("# 请选择生成风格，有两种风格可选 \n", "### 扁平风格 \n", "![img](/img.jpg) \n", "### 政务风格 \n", "![img](/img.jpg) \n"));
        Flux<GraphResponse<StreamingOutput>> generator = stringFlux.map(v->{
           return GraphResponse.of(new StreamingOutput(v, "motherboadrChoiceNode", state));
        }).concatWith(Mono.just(GraphResponse.done(Map.of("motherboadrChoiceNode", "母版选择") )));
        return Map.of("motherboadrChoiceNode", generator);

    }
}

package com.gengzi.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.FluxConverter;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.gengzi.tool.ppt.config.AiPPTConfig;
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

import java.util.Map;

/**
 * 大纲生成节点
 */
@Component
public class OutlineGenerationNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(OutlineGenerationNode.class);
    @Autowired
    private AiPPTConfig aiPPTConfig;


    @Autowired
    @Qualifier("deepseekChatClientNoRag")
    private ChatClient chatClient;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        logger.info("开始执行大纲生成节点");
        PromptTemplate DEFAULTPROMPTTEMPLATE = new PromptTemplate(aiPPTConfig.getOutlinePrompt());
        // 获取入参信息，通过入参调用llm 生成ppt大纲（流示输出 + 人类反馈）
        String query = state.value("query", "");

        Flux<ChatResponse> chatResponseFlux = this.chatClient.prompt()
                .system(DEFAULTPROMPTTEMPLATE.getTemplate())
                .user(query)
                .stream().chatResponse();



        // 异步内容到前端显示
        Flux<GraphResponse<StreamingOutput>> generator = FluxConverter.builder()
                .startingNode("outlineGenNode")
                .startingState(state)
                .mapResult(DefaultMapToResult.builder("outlineGenNode_content").build())
                .build(chatResponseFlux)
                .doOnComplete(()->{
                    logger.info("大纲生成完毕");
                });

        // TODO 可能影响数据读取
        Flux<GraphResponse<StreamingOutput>> outlineGenerationNodeStream =
                generator.concatWith(Mono.just(GraphResponse.of(new StreamingOutput("\n\n大纲生成完毕，请看下是否可行，不可行请提出修改建议\n\n", "outlineGenNode", state))));

        return Map.of("outlineGenNode_content", outlineGenerationNodeStream);

    }
}

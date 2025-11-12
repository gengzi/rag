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
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
        String conversationId = state.value("conversationId", "");

        Flux<ChatResponse> chatResponseFlux = this.chatClient.prompt()
                .system(DEFAULTPROMPTTEMPLATE.getTemplate())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(query)
                .stream().chatResponse();

        var outlineGenNodeContentMap = new AtomicReference<Map<String, Object>>(null);

        var streamingOutput = new StreamingOutput("\n\n大纲生成完毕，请看下是否可行，不可行请提出修改建议\n\n", "outlineGenNode", state);
        // 异步内容到前端显示
        Flux<GraphResponse<StreamingOutput>> generator = FluxConverter.builder()
                .startingNode("outlineGenNode")
                .startingState(state)
                .mapResult(chatResponse -> {
                    outlineGenNodeContentMap.set(DefaultMapToResult.builder("outlineGenNode_content").build().apply(chatResponse));
                    return outlineGenNodeContentMap.get();
                })
                .build(chatResponseFlux, streamingOutput)
                .doOnComplete(() -> {
                    logger.info("大纲生成完毕");
                });

        // **不能这样写，会导致返回流数据不存入 OverAllState中，因为 Flux<GraphResponse<StreamingOutput>> generator = FluxConverter.builder() 方法中就已经将数据进行合并 设置到
        // concatWith 顺序拼接两个响应式流（Publisher），确保第一个响应式流完成后，再执行第二个响应式流。两个流可以返回类型，甚至是结构不同的数据
//        Flux<GraphResponse<StreamingOutput>> outlineGenerationNodeStream =
//                generator.concatWith(Mono.just(
//                        GraphResponse.of(new StreamingOutput("\n\n大纲生成完毕，请看下是否可行，不可行请提出修改建议\n\n", "outlineGenNode", state))
//                ).doOnNext(data -> {
//                    logger.debug("大纲生成完毕");
//                }));

        // 返回map后，会自动合并到全局状态数据中
        return Map.of("outlineGenNode_content", generator);

    }
}

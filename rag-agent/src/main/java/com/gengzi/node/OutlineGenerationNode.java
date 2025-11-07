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
        PromptTemplate DEFAULTPROMPTTEMPLATE = new PromptTemplate(aiPPTConfig.getOutlinePrompt());
        // 获取入参信息，通过入参调用llm 生成ppt大纲（流示输出 + 人类反馈）
        String query = state.value("query", "");

        Flux<ChatResponse> chatResponseFlux = this.chatClient.prompt()
                .system(DEFAULTPROMPTTEMPLATE.getTemplate())
                .user(query)
                .stream().chatResponse();

        // 异步内容到前端显示
        Flux<GraphResponse<StreamingOutput>> generator = FluxConverter.builder()
                .startingNode("OutlineGenerationNodeStream")
                .startingState(state)
                .mapResult(DefaultMapToResult.builder("OutlineGenerationNodeStream").build())
                .build(chatResponseFlux);

        return Map.of("OutlineGenerationResult", generator);

    }
}

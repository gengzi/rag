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
public class PPTGenerationNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(PPTGenerationNode.class);
    @Autowired
    private AiPPTConfig aiPPTConfig;


    @Autowired
    @Qualifier("deepseekChatClientNoRag")
    private ChatClient chatClient;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        logger.info("开始执行ppt生成");
        String feedback = state.value("human_feedback", "");
        logger.info("用户反馈：{}", feedback);

        return Map.of("file_path", "");

    }
}

package com.gengzi.ragagent.deepresearch.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.gengzi.ragagent.deepresearch.config.DeepResearchConfig;
import com.gengzi.ragagent.deepresearch.config.NodeConfig;
import com.gengzi.ragagent.util.ResourceUtil;
import com.gengzi.ragagent.util.StateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;


/**
 * 用户意图识别节点
 * 用于判断用户是闲聊还是问答
 */
public class CoordinatorNode extends AbstractLlmNodeAction {

    private static final Logger logger = LoggerFactory.getLogger(CoordinatorNode.class);

    private final DeepResearchConfig deepResearchConfig;

    private final OpenAiChatModel.Builder openAiChatModelBuilder;

    public CoordinatorNode(DeepResearchConfig deepResearchConfig, OpenAiChatModel.Builder openAiChatModelBuilder) {
        this.deepResearchConfig = deepResearchConfig;
        this.openAiChatModelBuilder = openAiChatModelBuilder;
    }

    /**
     * @param state
     * @return
     * @throws Exception
     */
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        logger.info("coordinator node is running.");
        // 获取入参
        String query = StateUtil.getQuery(state);
        // 执行具体业务
        ChatClient chatClient = bulidChatClient();
        ChatResponse response = chatClient.prompt()
                .system(bulidPromptTemplate().getTemplate())
                .toolCallbacks(bulidToolCallback())
                .user(query)
                .call()
                .chatResponse();

        HashMap<String, Object> resultMap = new HashMap<>();


        if (response.hasToolCalls()) {
            //  工具已经调用
            resultMap.put(getNodeConfig().getNextNodeKey(), "rewrite_multi_query");
            resultMap.put("deep_research", true);
        } else {
            resultMap.put(getNodeConfig().getNextNodeKey(), END);
            resultMap.put("output", response.getResult().getOutput().getText());
        }
        // 赋值出参
        return resultMap;
    }

    /**
     * @return
     */
    @Override
    ChatClient bulidChatClient() {
        OpenAiChatModel openAiChatModel = openAiChatModelBuilder.defaultOptions(
                OpenAiChatOptions.builder()
                        .model(getNodeConfig().getModel())
                        .build()
        ).build();
        return ChatClient.builder(openAiChatModel).build();
    }

    private NodeConfig getNodeConfig() {
        Map<String, NodeConfig> deepresearchNodes = deepResearchConfig.getDeepresearchNodes();
        return deepresearchNodes.get(this.getClass().getName());
    }

    /**
     * @return
     */
    @Override
    PromptTemplate bulidPromptTemplate() {
        Map<String, NodeConfig> deepresearchNodes = deepResearchConfig.getDeepresearchNodes();
        if (deepresearchNodes.containsKey(this.getClass().getName())) {
            return PromptTemplate.builder()
                    .template(ResourceUtil.loadFileContent(getNodeConfig().getPrompts().get(0)
                            .replace("{{ CURRENT_TIME }}", LocalDateTime.now().toString())))
                    .build();
        }
        return null;
    }

    /**
     * @return
     */
    @Override
    ToolCallback[] bulidToolCallback() {
        Map<String, NodeConfig> deepresearchNodes = deepResearchConfig.getDeepresearchNodes();
        if (deepresearchNodes.containsKey(this.getClass().getName())) {
            return ToolCallbacks.from(getNodeConfig().getTools());
        }
        return ToolCallbacks.from();
    }
}

package com.gengzi.rag.agent.deepresearch.node;

import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.gengzi.rag.agent.deepresearch.config.DeepResearchConfig;
import com.gengzi.rag.agent.deepresearch.config.NodeConfig;
import com.gengzi.rag.agent.deepresearch.util.ResourceUtil;
import com.gengzi.rag.agent.deepresearch.util.StateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;


/**
 * 用户意图识别节点
 * 用于判断用户是闲聊还是问答
 */
public class PlannerNode extends AbstractLlmNodeAction {

    private static final Logger logger = LoggerFactory.getLogger(PlannerNode.class);

    private final DeepResearchConfig deepResearchConfig;

    private final OpenAiChatModel.Builder openAiChatModelBuilder;

    public PlannerNode(DeepResearchConfig deepResearchConfig, OpenAiChatModel.Builder openAiChatModelBuilder) {
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
        return ChatClient.builder(openAiChatModel)
                // 禁用内部工具执行 否则会自动执行工具调用，无法获取到是否需要调用工具
                .defaultOptions(ToolCallingChatOptions.builder()
                        .internalToolExecutionEnabled(false)
                        .build()).build();
    }

    private NodeConfig getNodeConfig() {
        Map<String, NodeConfig> deepresearchNodes = deepResearchConfig.getDeepresearchNodes();
        return deepresearchNodes.get(this.getClass().getSimpleName());
    }

    /**
     * @return
     */
    @Override
    PromptTemplate bulidPromptTemplate() {
        Map<String, NodeConfig> deepresearchNodes = deepResearchConfig.getDeepresearchNodes();
        if (deepresearchNodes.containsKey(this.getClass().getSimpleName())) {
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
        if (deepresearchNodes.containsKey(this.getClass().getSimpleName())) {
            List<String> tools = getNodeConfig().getTools();
            ArrayList<Object> objects = new ArrayList<>();
            for (String tool : tools) {
                Object bean = SpringUtil.getBean(tool);
                objects.add(bean);
            }
            return ToolCallbacks.from(objects.toArray());
        }
        return ToolCallbacks.from();
    }
}


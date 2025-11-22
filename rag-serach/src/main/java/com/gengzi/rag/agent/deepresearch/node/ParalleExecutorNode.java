package com.gengzi.rag.agent.deepresearch.node;

import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.gengzi.rag.agent.deepresearch.config.DeepResearchConfig;
import com.gengzi.rag.agent.deepresearch.config.NodeConfig;
import com.gengzi.rag.agent.deepresearch.dto.Plan;
import com.gengzi.rag.agent.deepresearch.util.ResourceUtil;
import com.gengzi.rag.agent.deepresearch.util.StateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 并行节点，用于执行并行任务
 */
public class ParalleExecutorNode extends AbstractLlmNodeAction {

    private static final Logger logger = LoggerFactory.getLogger(ParalleExecutorNode.class);

    private final DeepResearchConfig deepResearchConfig;

    private final OpenAiChatModel.Builder openAiChatModelBuilder;


    public ParalleExecutorNode(DeepResearchConfig deepResearchConfig, OpenAiChatModel.Builder openAiChatModelBuilder) {
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
        Plan plan = StateUtil.getPlan(state);
        // 根据 setp 并行执行检索任务

        List<Plan.Step> steps = plan.getSteps();
        List<Flux<ChatResponse>> chatResponses = steps.parallelStream().filter(Plan.Step::isNeedWebSearch).map(step -> {
            Flux<ChatResponse> chatResponseFlux = bulidChatClient().prompt()
                    .system(bulidPromptTemplate().getTemplate())
                    .user(step.getTitle() + "\n" + step.getDescription())
                    .stream().chatResponse();
            return chatResponseFlux;
        }).collect(Collectors.toList());
        Flux<ChatResponse> merge = Flux.mergeSequential(chatResponses);
        // 赋值出参
        return Map.of("paralleSearchResult", merge);
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
                                    .replace("{{ CURRENT_TIME }}", LocalDateTime.now().toString()))
                            .replace("{{ locale }}", "中国"))
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


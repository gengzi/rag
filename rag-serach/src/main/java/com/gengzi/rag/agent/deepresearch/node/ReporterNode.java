package com.gengzi.rag.agent.deepresearch.node;

import cn.hutool.core.util.StrUtil;
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
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
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
 * 信息节点
 */
public class ReporterNode extends AbstractLlmNodeAction {

    private static final Logger logger = LoggerFactory.getLogger(ReporterNode.class);

    private final DeepResearchConfig deepResearchConfig;

    private final OpenAiChatModel.Builder openAiChatModelBuilder;


    private final BeanOutputConverter<Plan> converter;

    public ReporterNode(DeepResearchConfig deepResearchConfig, OpenAiChatModel.Builder openAiChatModelBuilder) {
        this.deepResearchConfig = deepResearchConfig;
        this.openAiChatModelBuilder = openAiChatModelBuilder;
        this.converter = new BeanOutputConverter<>(Plan.class);

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
        String searchResult = StateUtil.getSearchResult(state);
        String ragResult = StateUtil.getRagResult(state);
        String paralleSearchResult = StateUtil.getParalleSearchResult(state);
        // 业务逻辑
        // 添加用户问题
        // 添加检索信息
        // 添加rag信息
        // 一起提供给llm，生成一篇完整的报告。
        List<String> optimizeQueries = StateUtil.getOptimizeQueries(state);
        ArrayList<Message> messages = new ArrayList<>();
        // 获取系统提示词
        SystemMessage systemMessage = new SystemMessage(bulidPromptTemplate().getTemplate());
        messages.add(systemMessage);
        // 获取原始和扩展后的问题
        String userQuery = "问题: %s 扩展后的问题: %s";
        UserMessage userMessageByQuery = new UserMessage(String.format(userQuery, query, optimizeQueries.stream().collect(Collectors.joining("\n"))));
        messages.add(userMessageByQuery);
        // 获取背景调查获取的内容
        UserMessage userMessageBySearchResult = new UserMessage("背景检索：" + searchResult);
        messages.add(userMessageBySearchResult);
        // 获取rag查询获取的内容
        UserMessage userMessageByRagContent = new UserMessage("本地知识库：" + ragResult);
        messages.add(userMessageByRagContent);
        // 获取深度检索内容
        UserMessage userMessageByParalleSearchResult = new UserMessage("深度研究：" + paralleSearchResult);
        messages.add(userMessageByParalleSearchResult);
        // 获取人类反馈（如果有）
        String feedbackContent = state.value("feedbackContent", "").toString();
        if (StrUtil.isNotBlank(feedbackContent)) {
            UserMessage userMessageByFeedbackContent = new UserMessage("人类反馈：" + feedbackContent);
            messages.add(userMessageByFeedbackContent);
        }
        // 调用llm
        Flux<ChatResponse> chatResponseFlux = bulidChatClient()
                .prompt()
                .messages(messages)
                .stream()
                .chatResponse();
        // 赋值出参
        return Map.of("reporterResult", chatResponseFlux);
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


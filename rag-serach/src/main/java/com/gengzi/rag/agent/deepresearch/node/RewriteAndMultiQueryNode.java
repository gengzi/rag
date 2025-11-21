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
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 重写用户query，优化查询冗余，模糊，不包含相关信息
 * 扩展用户query，从单个角色扩展为多个语义上的变体
 * <p>
 * 优化后的query 为 list
 */
public class RewriteAndMultiQueryNode extends AbstractLlmNodeAction {

    private static final Logger logger = LoggerFactory.getLogger(RewriteAndMultiQueryNode.class);

    private final DeepResearchConfig deepResearchConfig;

    private final OpenAiChatModel.Builder openAiChatModelBuilder;

    // 重写问题
    private RewriteQueryTransformer rewriteQueryTransformer;

    private ChatClient.Builder chatClientBuilder;

    private int optimizeQueryNum;

    public RewriteAndMultiQueryNode(DeepResearchConfig deepResearchConfig, OpenAiChatModel.Builder openAiChatModelBuilder, int optimizeQueryNum) {
        this.deepResearchConfig = deepResearchConfig;
        this.openAiChatModelBuilder = openAiChatModelBuilder;
        this.optimizeQueryNum = optimizeQueryNum;
        chatClientBuilder = ChatClient.builder(openAiChatModelBuilder.defaultOptions(
                OpenAiChatOptions.builder()
                        .model(getNodeConfig().getModel())
                        .build()
        ).build());

        rewriteQueryTransformer = RewriteQueryTransformer.builder().chatClientBuilder(chatClientBuilder).build();


    }

    /**
     * @param state
     * @return
     * @throws Exception
     */
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        logger.info("RewriteAndMultiQueryNode  is running.");
        // 获取入参
        String query = StateUtil.getQuery(state);
        // 执行具体业务
        // 问题重写
        Query transform = rewriteQueryTransformer.transform(Query.builder().text(query).build());
        // 问题扩展
        MultiQueryExpander multiQueryExpander = MultiQueryExpander.builder().chatClientBuilder(chatClientBuilder).includeOriginal(true).numberOfQueries(optimizeQueryNum).build();
        List<Query> queries = multiQueryExpander.expand(transform);
        // 赋值出参
        List<String> queriesText = queries.stream().map(q -> q.text()).collect(Collectors.toList());
        HashMap<String, Object> resultMap = new HashMap<>();

        // 下一个节点 并行（背景调查+rag检索）
        resultMap.put("optimize_queries",queriesText);
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


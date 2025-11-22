package com.gengzi.rag.agent.deepresearch.node;

import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.streaming.GraphFlux;
import com.alibaba.cloud.ai.toolcalling.common.interfaces.SearchService;
import com.gengzi.rag.agent.deepresearch.config.DeepResearchConfig;
import com.gengzi.rag.agent.deepresearch.config.NodeConfig;
import com.gengzi.rag.agent.deepresearch.util.ResourceUtil;
import com.gengzi.rag.agent.deepresearch.util.StateUtil;
import com.gengzi.rag.search.service.ChatRagService;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 用户意图识别节点
 * 用于判断用户是闲聊还是问答
 */
public class BackgroundInvectigationNode extends AbstractLlmNodeAction {

    public static final String SEARCHRESULT = "搜索问题: %s \n 以下是搜索结果: \n\n %s";
    public static final String SEARCHRESULT_CONTENT = "标题: %s\n 内容:%s\n url:%s\n ";
    private static final Logger logger = LoggerFactory.getLogger(BackgroundInvectigationNode.class);
    private final DeepResearchConfig deepResearchConfig;
    private final OpenAiChatModel.Builder openAiChatModelBuilder;
    private final SearchService tavilySearch;

    public BackgroundInvectigationNode(DeepResearchConfig deepResearchConfig, OpenAiChatModel.Builder openAiChatModelBuilder, SearchService tyavilySearch) {
        this.deepResearchConfig = deepResearchConfig;
        this.openAiChatModelBuilder = openAiChatModelBuilder;
        this.tavilySearch = tyavilySearch;
    }

    /**
     * @param state
     * @return
     * @throws Exception
     */
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        logger.info("BackgroundInvectigation node is running.");
        // 获取入参
        String query = StateUtil.getQuery(state);
        List<String> optimizeQueries = StateUtil.getOptimizeQueries(state);
        // 执行具体业务
        List<Mono<String>> searchResult = optimizeQueries.stream().map(q -> search(q)).collect(Collectors.toList());
        Flux<ChatResponse> searchMerge = Flux.mergeSequential(searchResult).collect(Collectors.joining("\n\n")).flatMapMany(
                result -> {
                    Flux<ChatResponse> chatResponseFlux = bulidChatClient().prompt()
                            .user(result)
                            .stream().chatResponse();
                    return chatResponseFlux;
                }
        );
//        StringBuffer stringBuilder = new StringBuffer();

        // GraphFlux 只适用于存储最后一个分片结果
//        GraphFlux<String> stringGraphFlux = GraphFlux.of("BackgroundInvectigationNode",
//                "searchResult",
//                searchMerge,
//                (chunkEnd) -> stringBuilder.append(chunkEnd).toString(),
//                chunk -> {
//                    stringBuilder.append(chunk);
//                    return chunk;
//                }
//        );

        // 赋值出参
        return Map.of("searchResult", searchMerge);
    }

    private Mono<String> search(String optimizeQuery) {
        return Mono.fromCallable(
                        () -> {
                            // 改为异步调用，同时获取结果
                            SearchService.Response response = tavilySearch.query(optimizeQuery);
                            StringBuilder sb = new StringBuilder();
                            List<SearchService.SearchContent> results = response.getSearchResult().results();
                            results.forEach(result -> {
                                logger.debug("title: {}", result.title());
                                logger.debug("content: {}", result.content());
                                String content = String.format(SEARCHRESULT_CONTENT, result.title(), result.content(), result.url());
                                sb.append(content);
                            });
                            String result = String.format(SEARCHRESULT, optimizeQuery, sb.toString());
                            return result;
                        }
                ).subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(optimizeQuery);

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


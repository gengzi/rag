package com.gengzi.rag.agent.deepresearch.node;

import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.streaming.GraphFlux;
import com.gengzi.rag.agent.deepresearch.config.DeepResearchConfig;
import com.gengzi.rag.agent.deepresearch.config.NodeConfig;
import com.gengzi.rag.agent.deepresearch.util.ResourceUtil;
import com.gengzi.rag.agent.deepresearch.util.StateUtil;
import com.gengzi.rag.search.service.ChatRagService;
import com.gengzi.request.ChatReq;
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
public class RagNode extends AbstractLlmNodeAction {

    private static final Logger logger = LoggerFactory.getLogger(RagNode.class);

    private final DeepResearchConfig deepResearchConfig;

    private final OpenAiChatModel.Builder openAiChatModelBuilder;

    private final ChatRagService chatRagService;

    public RagNode(DeepResearchConfig deepResearchConfig, OpenAiChatModel.Builder openAiChatModelBuilder, ChatRagService chatRagService) {
        this.deepResearchConfig = deepResearchConfig;
        this.openAiChatModelBuilder = openAiChatModelBuilder;
        this.chatRagService = chatRagService;
    }

    /**
     * @param state
     * @return
     * @throws Exception
     */
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        logger.info("coordinator node is running.");
        String userId = StateUtil.getUserId(state);
        List<String> optimizeQueries = StateUtil.getOptimizeQueries(state);
        String conversationId = StateUtil.getConversationId(state);
        // 业务逻辑，查询rag信息

        List<Flux<ChatResponse>> collect = optimizeQueries.stream().map(q -> {
            ChatReq chatReq = new ChatReq();
            chatReq.setQuery(q);
            chatReq.setConversationId(conversationId);
            return chatRagService.chatRagByAgent(chatReq, userId);
        }).collect(Collectors.toList());
        Flux<ChatResponse> flux = Flux.mergeSequential(collect).subscribeOn(Schedulers.boundedElastic());
//        StringBuffer stringBuilder = new StringBuffer();
//        GraphFlux<String> stringGraphFlux = GraphFlux.of("RagNode",
//                "ragResult",
//                flux,
//                (chunkEnd) -> stringBuilder.append(chunkEnd).toString(),
//                chunk -> {
//                    stringBuilder.append(chunk);
//                    return chunk;
//                }
//        );

        // 赋值出参
        return Map.of("ragResult", flux);
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


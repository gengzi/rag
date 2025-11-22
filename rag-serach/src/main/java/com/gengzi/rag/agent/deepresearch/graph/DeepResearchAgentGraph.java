package com.gengzi.rag.agent.deepresearch.graph;


import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.cloud.ai.toolcalling.common.interfaces.SearchService;
import com.gengzi.rag.agent.deepresearch.config.DeepResearchConfig;
import com.gengzi.rag.agent.deepresearch.dispathcher.CoordinatorDispatcher;
import com.gengzi.rag.agent.deepresearch.dispathcher.InformationDispatcher;
import com.gengzi.rag.agent.deepresearch.node.*;
import com.gengzi.rag.search.service.ChatRagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Configuration
public class DeepResearchAgentGraph {

    private static final Logger logger = LoggerFactory.getLogger(DeepResearchAgentGraph.class);

    @Autowired
    private DeepResearchConfig deepResearchConfig;

    @Autowired
    private OpenAiChatModel.Builder openAiChatModelBuilder;


    @Autowired
    private SearchService tavilySearch;

    @Autowired
    private ChatRagService chatRagService;

    /**
     * 构建深度检索图链路
     *
     * @return
     */
    @Bean
    public StateGraph deepResearch() throws GraphStateException {

        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
            // 条件边控制：跳转下一个节点
            keyStrategyHashMap.put("CoordinatorNode_next_node", new ReplaceStrategy());
            keyStrategyHashMap.put("rewrite_multi_query_next_node", new ReplaceStrategy());
            keyStrategyHashMap.put("InformationNode_next_node", new ReplaceStrategy());
            // 用户输入
            keyStrategyHashMap.put("query", new ReplaceStrategy());
            keyStrategyHashMap.put("threadId", new ReplaceStrategy());
            keyStrategyHashMap.put("userId", new ReplaceStrategy());
            // 输出节点
            keyStrategyHashMap.put("output", new ReplaceStrategy());
            keyStrategyHashMap.put("optimize_queries", new ReplaceStrategy());
            keyStrategyHashMap.put("ragResult", new ReplaceStrategy());
            keyStrategyHashMap.put("searchResult", new ReplaceStrategy());
            keyStrategyHashMap.put("plannerResult", new ReplaceStrategy());
            keyStrategyHashMap.put("planMaxIterations", new ReplaceStrategy());
            keyStrategyHashMap.put("paralleSearchResult", new ReplaceStrategy());
            keyStrategyHashMap.put("reporterResult", new ReplaceStrategy());
            return keyStrategyHashMap;
        };

        StateGraph stateGraph = new StateGraph("deep research", keyStrategyFactory)
                // 用户问题分类节点
                .addNode("CoordinatorNode", node_async(new CoordinatorNode(deepResearchConfig, openAiChatModelBuilder)))
                // 问题重写节点
                .addNode("RewriteAndMultiQueryNode", node_async(new RewriteAndMultiQueryNode(deepResearchConfig, openAiChatModelBuilder, 2)))
                .addNode("BackgroundInvectigationNode", node_async(new BackgroundInvectigationNode(deepResearchConfig, openAiChatModelBuilder, tavilySearch)))
                .addNode("RagNode", node_async(new RagNode(deepResearchConfig, openAiChatModelBuilder, chatRagService)))
                .addNode("PlannerNode", node_async(new PlannerNode(deepResearchConfig, openAiChatModelBuilder, 3)))
                .addNode("InformationNode", node_async(new InformationNode(deepResearchConfig, openAiChatModelBuilder, 3)))
                .addNode("ParalleExecutorNode", node_async(new ParalleExecutorNode(deepResearchConfig, openAiChatModelBuilder)))
                .addNode("ReporterNode", node_async(new ReporterNode(deepResearchConfig, openAiChatModelBuilder)));


        stateGraph.addEdge(START, "CoordinatorNode")
                // 意图识别-》问题重写
                .addConditionalEdges("CoordinatorNode", AsyncEdgeAction.edge_async(new CoordinatorDispatcher(deepResearchConfig, "CoordinatorNode")),
                        Map.of("RewriteAndMultiQueryNode", "RewriteAndMultiQueryNode", END, END))
                // 并行执行 - 》背景调查
                .addEdge("RewriteAndMultiQueryNode", "BackgroundInvectigationNode")
                // -》 rag内容获取
                .addEdge("BackgroundInvectigationNode", "RagNode")
                //  规划节点
                .addEdge("RagNode", "PlannerNode")
                // 信息判断节点
                .addEdge("PlannerNode", "InformationNode")
                .addConditionalEdges("InformationNode",
                        AsyncEdgeAction.edge_async(new InformationDispatcher(deepResearchConfig, "InformationNode")),
                        Map.of("PlannerNode", "PlannerNode", "ParalleExecutorNode", "ParalleExecutorNode", "ReporterNode", "ReporterNode", END, END))
                .addEdge("ParalleExecutorNode","ReporterNode")
                .addEdge("ReporterNode", END);

        GraphRepresentation graphRepresentation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML,
                "workflow graph");

        logger.info("\n\n");
        logger.info(graphRepresentation.content());
        logger.info("\n\n");

        return stateGraph;
    }

}

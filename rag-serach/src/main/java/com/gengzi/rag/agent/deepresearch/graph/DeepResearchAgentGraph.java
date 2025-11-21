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
import com.gengzi.rag.agent.deepresearch.node.*;
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
            // 用户输入
            keyStrategyHashMap.put("query", new ReplaceStrategy());
            keyStrategyHashMap.put("thread_id", new ReplaceStrategy());
            // 输出节点
            keyStrategyHashMap.put("output", new ReplaceStrategy());
            keyStrategyHashMap.put("optimize_queries", new ReplaceStrategy());


            return keyStrategyHashMap;
        };

        StateGraph stateGraph = new StateGraph("deep research", keyStrategyFactory)
                // 用户问题分类节点
                .addNode("CoordinatorNode", node_async(new CoordinatorNode(deepResearchConfig, openAiChatModelBuilder)))
                // 问题重写节点
                .addNode("RewriteAndMultiQueryNode", node_async(new RewriteAndMultiQueryNode(deepResearchConfig, openAiChatModelBuilder, 3)))
                .addNode("BackgroundInvectigationNode", node_async(new BackgroundInvectigationNode(deepResearchConfig, openAiChatModelBuilder,tavilySearch)))
                .addNode("RagNode", node_async(new RagNode(deepResearchConfig, openAiChatModelBuilder)))
                .addNode("PlannerNode", node_async(new PlannerNode(deepResearchConfig, openAiChatModelBuilder)));


        stateGraph.addEdge(START, "CoordinatorNode")
                // 意图识别-》问题重写
                .addConditionalEdges("CoordinatorNode", AsyncEdgeAction.edge_async(new CoordinatorDispatcher(deepResearchConfig, "CoordinatorNode")),
                        Map.of("RewriteAndMultiQueryNode", "RewriteAndMultiQueryNode", END, END))
                // 并行执行 - 》背景调查
                .addEdge("RewriteAndMultiQueryNode", "BackgroundInvectigationNode")
                 // -》 rag内容获取
                .addEdge("RewriteAndMultiQueryNode", "RagNode")
                //  汇总到 规划节点
                .addEdge("BackgroundInvectigationNode", "PlannerNode")
                .addEdge("RagNode", "PlannerNode")
                .addEdge("PlannerNode", END);

        GraphRepresentation graphRepresentation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML,
                "workflow graph");

        logger.info("\n\n");
        logger.info(graphRepresentation.content());
        logger.info("\n\n");

        return stateGraph;
    }

}

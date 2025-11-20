package com.gengzi.rag.agent.deepresearch.graph;


import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.gengzi.rag.agent.deepresearch.config.DeepResearchConfig;
import com.gengzi.rag.agent.deepresearch.dispathcher.CoordinatorDispatcher;
import com.gengzi.rag.agent.deepresearch.node.CoordinatorNode;
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
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Configuration
public class DeepResearchAgentGraph {

    private static final Logger logger = LoggerFactory.getLogger(DeepResearchAgentGraph.class);

    @Autowired
    private DeepResearchConfig deepResearchConfig;

    @Autowired
    private OpenAiChatModel.Builder openAiChatModelBuilder;


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
            keyStrategyHashMap.put("coordinator_next_node", new ReplaceStrategy());
            keyStrategyHashMap.put("rewrite_multi_query_next_node", new ReplaceStrategy());

            // 用户输入
            keyStrategyHashMap.put("query", new ReplaceStrategy());
            keyStrategyHashMap.put("thread_id", new ReplaceStrategy());
            // 输出节点
            keyStrategyHashMap.put("output", new ReplaceStrategy());
            keyStrategyHashMap.put("coordinator_next_node", new ReplaceStrategy());

            return keyStrategyHashMap;
        };

        StateGraph stateGraph = new StateGraph("deep research", keyStrategyFactory)
                // 用户问题分类节点
                .addNode("CoordinatorNode", node_async(new CoordinatorNode(deepResearchConfig, openAiChatModelBuilder)));


        stateGraph.addEdge(START, "CoordinatorNode")
//                .addConditionalEdges("CoordinatorNode", edge_async(new CoordinatorDispatcher(deepResearchConfig, "CoordinatorNode")),
//                        Map.of("CoordinatorNode", "CoordinatorNode", END, END))
                .addEdge("CoordinatorNode", END);

        GraphRepresentation graphRepresentation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML,
                "workflow graph");

        logger.info("\n\n");
        logger.info(graphRepresentation.content());
        logger.info("\n\n");

        return stateGraph;
    }

}

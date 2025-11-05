package com.gengzi.node;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.internal.node.SubStateGraphNode;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;


/**
 * 图配置
 */
@Configuration
public class GraphConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(GraphConfiguration.class);

    @Bean
    public StateGraph simpleGraph(ChatClient.Builder chatClientBuilder) throws GraphStateException {
        // 定义一个键策略工厂，用于定义键的替换策略
        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
            // 用户输入
            keyStrategyHashMap.put("query", new ReplaceStrategy());
            keyStrategyHashMap.put("expandernumber", new ReplaceStrategy());
            keyStrategyHashMap.put("expandercontent", new ReplaceStrategy());
            return keyStrategyHashMap;
        };
//
//        StateGraph stateGraph1 = new StateGraph(keyStrategyFactory)
//                .addNode("node4", AsyncNodeAction.node_async(new TestNode(chatClientBuilder)))
//                .addNode("node5", AsyncNodeAction.node_async(new TestNode(chatClientBuilder)))
//                .addNode("node6", AsyncNodeAction.node_async(new TestNode(chatClientBuilder)))
//                .addNode("node7", AsyncNodeAction.node_async(new TestNode(chatClientBuilder)))
//                .addEdge(StateGraph.START, "node4")
//                .addEdge("node4", "node5")
//                .addEdge("node4", "node6")
//                .addEdge("node5", "node7")
//                .addEdge("node6", "node7")
//                .addEdge("node7", StateGraph.END)
//                ;
//
//        StateGraph stateGraph = new StateGraph(keyStrategyFactory)
//                .addNode("expander",  AsyncNodeAction.node_async(new ExpanderNode(chatClientBuilder)))
//                .addNode("node3", AsyncNodeAction.node_async(new TestNode(chatClientBuilder)))
//                .addNode("sub",  new SubStateGraphNode("sub",stateGraph1))
//                .addNode("node8", AsyncNodeAction.node_async(new TestNode(chatClientBuilder)))
//                .addEdge(StateGraph.START, "expander")
//                .addEdge("expander", "node3")
//                .addEdge("expander", "sub")
//                .addEdge("sub", "node8")
//                .addEdge("node3", "node8")
//                .addEdge("node8", StateGraph.END);

        StateGraph stateGraph2 = new StateGraph(keyStrategyFactory)
                .addNode("expander",  AsyncNodeAction.node_async(new ExpanderNode(chatClientBuilder)))
                .addEdge(StateGraph.START, "expander")
                .addEdge("expander", StateGraph.END);

        // 添加 PlantUML 打印
        GraphRepresentation representation = stateGraph2.getGraph(GraphRepresentation.Type.PLANTUML,
                "expander flow");
        logger.info("\n=== expander UML Flow ===");
        logger.info(representation.content());
        logger.info("==================================\n");

        return stateGraph2;
    }
}
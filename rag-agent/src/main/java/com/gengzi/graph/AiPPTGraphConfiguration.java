package com.gengzi.graph;

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.gengzi.dispatcher.HumanFeedbackDispatcher;
import com.gengzi.node.HumanFeedbackNode;
import com.gengzi.node.OutlineGenerationNode;
import com.gengzi.node.PPTGenerationNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;


/**
 * 对aipptgraph 工作流程的定义和配置
 */
@Configuration
public class AiPPTGraphConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AiPPTGraphConfiguration.class);

    @Autowired
    private OutlineGenerationNode outlineGenerationNode;

    @Autowired
    private HumanFeedbackNode humanFeedbackNode;

    @Autowired
    private PPTGenerationNode pptGenerationNode;

    @Bean
    public StateGraph streamGraph() throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
            // 用户输入
            keyStrategyHashMap.put("query", new ReplaceStrategy());


            keyStrategyHashMap.put("outline_content", new ReplaceStrategy());
            keyStrategyHashMap.put("outlineGenNode_content", new ReplaceStrategy());

            // 人类反馈
            keyStrategyHashMap.put("feedback", new ReplaceStrategy());
            keyStrategyHashMap.put("human_next_node", new ReplaceStrategy());
            keyStrategyHashMap.put("human_feedback", new ReplaceStrategy());

            keyStrategyHashMap.put("feedback_data", new ReplaceStrategy());

            keyStrategyHashMap.put("PPTGenerationNodeAgentStream", new ReplaceStrategy());
            keyStrategyHashMap.put("pptGenNode", new ReplaceStrategy());
            keyStrategyHashMap.put("file_path", new ReplaceStrategy());


            return keyStrategyHashMap;
        };

        StateGraph stateGraph = new StateGraph(keyStrategyFactory)
                // 意图识别节点（用户主题不是跟生成ppt相关内容，就提示无法生成）
                // 大纲生成节点
                .addNode("outlineGenNode", AsyncNodeAction.node_async(outlineGenerationNode))
                // 人类反馈节点
                .addNode("humanFeedbackNode", AsyncNodeAction.node_async(humanFeedbackNode))
                // ppt生成节点
                .addNode("pptGenNode", AsyncNodeAction.node_async(pptGenerationNode))




                // 添加边
                .addEdge(StateGraph.START, "outlineGenNode")
                .addEdge("outlineGenNode", "humanFeedbackNode")
                // 条件边
                .addConditionalEdges("humanFeedbackNode", AsyncEdgeAction.edge_async(new HumanFeedbackDispatcher()),
                        Map.of("outlineGenNode", "outlineGenNode", "pptGenNode", "pptGenNode"))
                .addEdge("pptGenNode", StateGraph.END);

        // 添加 PlantUML 打印
        GraphRepresentation representation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML,
                "expander flow");
        logger.info("\n=== expander UML Flow ===");
        logger.info(representation.content());
        logger.info("==================================\n");

        return stateGraph;
    }

}
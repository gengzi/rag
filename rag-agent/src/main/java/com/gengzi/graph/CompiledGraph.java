package com.gengzi.graph;


import com.gengzi.graph.edge.Edge;
import com.gengzi.graph.node.Node;
import com.gengzi.graph.node.ParallelNode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 状态图编译器 ，将定义的流程图转为一个可执行的流程
 */
public class CompiledGraph {


    /**
     * The State graph.
     */
    public final StateGraph stateGraph;

    /**
     * The Node Factories - stores factory functions instead of instances to ensure thread safety.
     * 节点id，节点本身
     */
    public final Map<String, Node> nodeFactories = new LinkedHashMap<>();

    /**
     * The Edges.  源id， 目标id
     */
    public final Map<String, String> edges = new LinkedHashMap<>();


    public CompiledGraph(StateGraph stateGraph) {
        this.stateGraph = stateGraph;
        stateGraph.nodes.getElements().forEach(node -> {
            nodeFactories.put(node.getId(), node);
        });
        List<Edge> elements = stateGraph.edges.getElements();
        for (Edge edge : elements) {
            // 单目标节点
            if (edge.targets().size() == 1) {
                edges.put(edge.sourceId(), (String) edge.targets().get(0));
            } else {
                // 多目标节点，需要并行执行    源节点 -> 目标节点1，目标节点2   -> 节点3 -> end
                ArrayList<Node> nodes = new ArrayList<>();
                for (Object target : edge.targets()) {
                    nodes.add(nodeFactories.get(target));
                }
                ParallelNode parallelNode = new ParallelNode(edge.sourceId(), nodes);
                nodeFactories.put(parallelNode.getId(), parallelNode);

                List<String> nodesIds = nodes.stream().map(node -> node.getId()).collect(Collectors.toList());


                Set<Edge> collect = elements.stream().filter(edge1 -> nodesIds.contains(edge1.sourceId())).collect(Collectors.toSet());
                Set<Object> collect1 = collect.stream().map(target -> target.targets().get(0)).collect(Collectors.toSet());

                if(collect1.size() > 1){
                    throw new RuntimeException("并行节点下一个节点不能有多个节点");
                }

                edges.put(edge.sourceId(), parallelNode.getId());
                edges.put(parallelNode.getId(), (String) collect.stream().findFirst().get().targets().get(0));

            }


        }


    }
}

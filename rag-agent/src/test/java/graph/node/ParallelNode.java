package graph.node;


import java.util.ArrayList;
import java.util.List;

/**
 * 并行节点，包装多个节点信息
 */
public class ParallelNode extends Node{


    private String sourceId;

    List<Node> nodes = new ArrayList<>();

    public ParallelNode(String id, List<Node> nodes){
        sourceId = id;
        setId("并行节点1");
        nodes.addAll(nodes);
    }


}

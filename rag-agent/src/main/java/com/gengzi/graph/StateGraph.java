package com.gengzi.graph;


import com.gengzi.graph.edge.Edge;
import com.gengzi.graph.node.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 状态图，用于管理 节点和边的关系 和 全局的状态
 * StateGraph（状态图，用于定义节点和边）、Node（节点，封装具体操作或模型调用）、Edge（边，表示节点间的跳转关系）以及 OverAllState（全局状态，贯穿流程共享数据）
 */
public class StateGraph {


    /**
     * Constant representing the END of the graph.
     */
    public static final String END = "__END__";

    /**
     * Constant representing the START of the graph.
     */
    public static final String START = "__START__";


    // 管理所有节点
    public Nodes nodes = new Nodes();

    // 管理所有边
    public Edges edges = new Edges();

    public StateGraph addNode(String nodeId) {
        Node node = new Node();
        node.setId(nodeId);
        nodes.elements.add(node);
        return this;
    }

    public StateGraph addEdge(String source, String target) {
        // 判断当前源节点是否存在，并与在图中有一样的，就把target 存放在一起
        ArrayList<Object> objects = new ArrayList<>();
        objects.add(target);
        Edge edge = new Edge(source, objects);
        int equalsIndex = edges.elements.indexOf(edge);
        // 存在同一个源id 的就将目标id合并在一起
        if (equalsIndex >= 0) {
            Edge edge1 = edges.elements.get(equalsIndex);
            List<Object> targets = edge1.targets();
            targets.add(target);
            edges.elements.add(new Edge(source, targets));
        } else {
            edges.elements.add(edge);
        }
        return this;
    }

    /**
     * 节点
     */
    public static class Nodes {
        private Set<Node> elements;

        public Nodes() {
            this.elements = new java.util.HashSet<>();
        }

        public Set<Node> getElements() {
            return elements;
        }

        public void setElements(Set<Node> elements) {
            this.elements = elements;
        }
    }

    /**
     * 边
     */
    public static class Edges {
        private List<Edge> elements;

        public Edges() {
            this.elements = new java.util.ArrayList<>();
        }

        public List<Edge> getElements() {
            return elements;
        }

        public void setElements(List<Edge> elements) {
            this.elements = elements;
        }
    }


}

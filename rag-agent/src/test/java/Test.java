import com.gengzi.graph.CompiledGraph;
import com.gengzi.graph.StateGraph;

import java.util.Map;

public class Test {


    public static void main(String[] args) {

        StateGraph stateGraph = new StateGraph();
        stateGraph.addNode("node1");
        stateGraph.addNode("node2");
        stateGraph.addNode("node3");
        stateGraph.addNode("node4");
        stateGraph.addEdge("node1", "node2");
        stateGraph.addEdge("node1", "node4");
        stateGraph.addEdge("node2", "node3");
        stateGraph.addEdge("node4", "node3");


        CompiledGraph compiledGraph = new CompiledGraph(stateGraph);

        Map<String, String> edges = compiledGraph.edges;

        for (Map.Entry<String, String> entry : edges.entrySet()) {
            System.out.print(entry.getKey() + " -> " + entry.getValue());
        }

    }

}

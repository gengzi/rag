package graph.edge;

import java.util.List;
import java.util.Objects;

/**
 *
 *            - 边的条件为true  -> nextNode
 *    node                                   -> end
 *            - 边的条件为false -> nextNode2
 *
 *
 */
public record Edge(String sourceId, List<Object> targets) {


//    // 源id
//    private String id;
//
//    // 目标 （可能是多个，其中还包含了条件，用于判断下一个节点，执行那个）
//    private List<?> targets;


    @Override
    public boolean equals(Object obj) {
       return Objects.equals(sourceId, ((Edge) obj).sourceId);
    }

}

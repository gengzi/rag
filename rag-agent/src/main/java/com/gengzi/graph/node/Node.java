package com.gengzi.graph.node;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;

public class Node {

    private String id;

    public interface ActionFactory {
        AsyncNodeActionWithConfig apply(CompileConfig config) throws GraphStateException;
    }




    // 异步执行node的任务
    private ActionFactory actionFactory;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ActionFactory getActionFactory() {
        return actionFactory;
    }

    public void setActionFactory(ActionFactory actionFactory) {
        this.actionFactory = actionFactory;
    }
}

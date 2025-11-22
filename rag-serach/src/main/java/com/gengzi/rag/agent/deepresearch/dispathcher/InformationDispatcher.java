package com.gengzi.rag.agent.deepresearch.dispathcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.gengzi.rag.agent.deepresearch.config.DeepResearchConfig;

import static com.alibaba.cloud.ai.graph.StateGraph.END;


public class InformationDispatcher implements EdgeAction {


    private final DeepResearchConfig deepResearchConfig;

    private final String nodeName;

    public InformationDispatcher(DeepResearchConfig deepResearchConfig, String nodeName) {
        this.deepResearchConfig = deepResearchConfig;
        this.nodeName = nodeName;
    }

    @Override
    public String apply(OverAllState state) {
        String nextNodeKey = deepResearchConfig.getDeepresearchNodes().get(nodeName).getNextNodeKey();
        return (String) state.value(nextNodeKey, END);
    }

}

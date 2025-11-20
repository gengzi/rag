package com.gengzi.rag.agent.deepresearch.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "deepresearch")
@Data
public class DeepResearchConfig {

    private Map<String, NodeConfig> nodes;

    public Map<String, NodeConfig> getDeepresearchNodes() {
        return nodes;
    }



    /**
     * 根据节点名称获取节点配置
     * @param nodeName 节点名称（CoordinatorNode/PlannerNode）
     * @return 节点配置，不存在返回 null
     */
    public NodeConfig getNodeConfig(String nodeName) {
        return nodes.getOrDefault(nodeName, null);
    }

    /**
     * 检查节点是否存在
     * @param nodeName 节点名称
     * @return 存在返回 true，否则 false
     */
    public boolean hasNode(String nodeName) {
        return nodes != null && nodes.containsKey(nodeName);
    }

}

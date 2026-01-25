package com.gengzi.ragagent.deepresearch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "deepresearch")
@Data
public class DeepResearchConfig {

    private Map<String, NodeConfig> deepresearchNodes;

    // 注意：这里的属性名必须和 YAML 中的 key 一致（coordinatorNode, plannerNode）
    // Spring Boot 会自动把 YAML 的子节点映射为 Map 的 entry

    // Getter 和 Setter
    public Map<String, NodeConfig> getDeepresearchNodes() {
        return deepresearchNodes;
    }

    public void setDeepresearchNodes(Map<String, NodeConfig> deepresearchNodes) {
        this.deepresearchNodes = deepresearchNodes;
    }

}

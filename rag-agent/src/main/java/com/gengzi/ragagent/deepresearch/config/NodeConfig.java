package com.gengzi.ragagent.deepresearch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// 单个节点的配置
@Data
public class NodeConfig {
    private String nodeName;
    private String displayTitle;
    private String model;
    private List<String> prompts;
    private List<String> tools;
    private String nextNodeKey;



    @Override
    public String toString() {
        return "NodeConfig{" +
                "nodeName='" + nodeName + '\'' +
                ", displayTitle='" + displayTitle + '\'' +
                ", model='" + model + '\'' +
                ", prompts=" + prompts +
                ", tools=" + tools +
                '}';
    }
}
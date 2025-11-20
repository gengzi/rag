package com.gengzi.rag.agent.deepresearch.config;

import lombok.Data;

import java.util.List;

// 单个节点的配置
@Data
public class NodeConfig {
    private String nodeName;
    private String displayTitle;
    private String nextNodeKey;
    private String model;
    private List<String> prompts;
    private List<String> tools;




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
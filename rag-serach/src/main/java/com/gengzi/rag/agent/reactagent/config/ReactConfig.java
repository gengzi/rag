package com.gengzi.rag.agent.reactagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.List;


/**
 * TODO react rag 对于其他模型执行有问题，后续再实现
 */
@Component
@ConfigurationProperties(prefix = "react")
public class ReactConfig {
    
    private Map<String, AgentConfig> agents;

    public Map<String, AgentConfig> getAgents() {
        return agents;
    }

    public void setAgents(Map<String, AgentConfig> agents) {
        this.agents = agents;
    }

    @Data
    public static class AgentConfig {
        private String name;
        private String model;
        // 指令
        private String instruction;


    }
}

package com.gengzi.enums;


/**
 * 代码中所有定义好的agent
 */
public enum Agent {
    DEEPRESEARCH_AGENT("DeepResearch", "深度检索", ""),
    PPTGENERATE_AGENT("PPTGenerate", "PPT生成", ""),
    ;
    private final String agentCode;
    private final String description;
    private final String agentGraphBeanName;

    Agent(String agentCode, String description, String agentGraphBeanName) {
        this.agentCode = agentCode;
        this.description = description;
        this.agentGraphBeanName = agentGraphBeanName;
    }

    public static Agent fromCode(String agentCode) {
        for (Agent type : Agent.values()) {
            if (type.agentCode.equals(agentCode)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown node agentCode: " + agentCode);
    }

    public static Boolean isExist(String agentCode) {
        for (Agent type : Agent.values()) {
            if (type.agentCode.equals(agentCode)) {
                return true;
            }
        }
       return false;
    }

    public String getCode() {
        return agentCode;
    }

    public String getDescription() {
        return description;
    }

    public String getAgentGraphBeanName() {
        return agentGraphBeanName;
    }
}
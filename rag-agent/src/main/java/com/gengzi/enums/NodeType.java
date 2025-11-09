package com.gengzi.enums;

public enum NodeType {
    OUTLINE_GEN_NODE("outlineGenNode", "大纲节点", "OutlineGenerationNodeTextStream"),
    HUMAN_FEEDBACK_NODE("humanFeedbackNode", "人类反馈节点", ""),
    PPT_GEN_NODE("pptGenNode", "ppt生成节点", "PPTGenerationNodeAgentStream"),
    ;
    private final String code;
    private final String description;
    private final String outputNode;

    NodeType(String code, String description, String outputNode) {
        this.code = code;
        this.description = description;
        this.outputNode = outputNode;
    }

    /**
     * 根据 code（如 "outlineGenNode"）查找对应的枚举值
     */
    public static NodeType fromCode(String code) {
        for (NodeType type : NodeType.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown node code: " + code);
    }

    /**
     *   根据 outputNode查找对应的枚举值
     */
    public static NodeType fromOutputNode(String outputNode) {
        for (NodeType type : NodeType.values()) {
            if (type.outputNode.equals(outputNode)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown node outputNode: " + outputNode);
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getOutputNode() {
        return outputNode;
    }
}
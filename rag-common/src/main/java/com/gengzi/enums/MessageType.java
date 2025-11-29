package com.gengzi.enums;

public enum MessageType {

    /**
     * 普通大模型回复（无工具调用，可能含 RAG 引用）
     */
    LLM_RESPONSE("text", "LLM 回复"),

    /**
     * 智能体（Agent）执行后的综合回复，包含思考链、工具调用等
     */
    AGENT_RESPONSE("agent", "Agent 回复"),


    /**
     * web 视图
     */
    WEB_VIEW("web", "web视图"),

    /**
     * END
     */
    END_OF_STREAM("end", "流已完成"),

    /**
     * LOCK
     */
    RLOCK("rlock", "正在对话中，请刷新页面"),


    /**
     * 智能体（Agent）执行后的综合回复，包含思考链、工具调用等
     */
    EXCALIDRAW("excalidraw", "绘图"),


    /**
     * 用户输入的消息
     */
    USER_INPUT("user_input", "用户消息"),


    /**
     * 系统消息（如会话初始化、错误提示、权限通知等）
     */
    SYSTEM_MESSAGE("system_message", "系统消息"),

    /**
     * 工具（Tool）执行的中间结果（通常不直接展示给用户，或以日志形式展示）
     */
    TOOL_RESPONSE("tool_response", "工具返回"),

    /**
     * 多轮 Agent 规划中的中间步骤（如 Plan-and-Execute 中的 plan 阶段）
     */
    PLANNER_STEP("planner_step", "规划步骤");

    /**
     * 前端/JSON 中使用的字符串值（建议小写 + 下划线，符合常规 API 风格）
     */
    private final String typeCode;

    /**
     * 人类可读的描述（用于日志、监控、管理后台）
     */
    private final String description;

    MessageType(String typeCode, String description) {
        this.typeCode = typeCode;
        this.description = description;
    }

    /**
     * 根据 typeCode（如 "agent_response"）反查枚举
     * 用于从 JSON 或数据库还原类型
     */
    public static MessageType fromTypeCode(String code) {
        for (MessageType type : MessageType.values()) {
            if (type.typeCode.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown MessageType code: " + code);
    }

    // Getter
    public String getTypeCode() {
        return typeCode;
    }

    // ========== 工具方法 ==========

    public String getDescription() {
        return description;
    }

    /**
     * 判断是否为“用户可见”的最终回复类型
     * （可用于前端决定是否展示在聊天主区域）
     */
    public boolean isUserVisibleResponse() {
        return this == LLM_RESPONSE || this == AGENT_RESPONSE || this == SYSTEM_MESSAGE;
    }

    /**
     * 判断是否由 AI 生成（非用户输入）
     */
    public boolean isAiGenerated() {
        return this != USER_INPUT;
    }
}
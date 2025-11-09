package com.gengzi.enums;

public enum ParticipantType {
    TEXT("text", "文本"),
    AGENT("agent", "智能体");

    private final String code;
    private final String description;

    ParticipantType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据 code（如 "text" 或 "agent"）查找对应的枚举值
     *
     * @param code 输入的字符串标识
     * @return 对应的枚举实例
     * @throws IllegalArgumentException 如果 code 无效
     */
    public static ParticipantType fromCode(String code) {
        for (ParticipantType type : ParticipantType.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid participant code: " + code);
    }
}
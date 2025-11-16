package com.gengzi.enums;

/**
 * 记忆类型枚举（对应 preference/fact/personality/goal）
 */
public enum MemoryType {
    PREFERENCE("preference"),  // 偏好
    FACT("fact"),              // 事实
    PERSONALITY("personality"),// 性格
    GOAL("goal");              // 目标

    private final String value;

    MemoryType(String value) {
        this.value = value;
    }

    // 获取枚举对应的字符串值（如 PREFERENCE → "preference"）
    public String getValue() {
        return value;
    }
}
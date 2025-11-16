package com.gengzi.entity;

import com.gengzi.enums.MemoryType;
import lombok.Data;

/**
 * 用户长期记忆信息实体类（映射 JSON 结构）
 */
@Data
public class UserMemory {
    /**
     * 记忆内容（用户陈述或推导的具体信息）
     */
    private String content;

    /**
     * 记忆类型（通过枚举约束，只能是预定义的 4 种类型）
     */
    private String type;

    /**
     * 置信度（范围 0.5~1.0，精确到小数点后 1 位，仅支持 0.5/0.7/0.9/1.0）
     */
    private Float confidence;
}
package com.gengzi.enums;

public enum TaskStatus {
    EXECUTING(0, "执行中"),
    COMPLETED(1, "已完成"),
    FAILED(2, "失败");

    private final int code;
    private final String description;

    TaskStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    // 可选：根据 code 获取对应的枚举值
    public static TaskStatus fromCode(int code) {
        for (TaskStatus status : TaskStatus.values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid code: " + code);
    }
}
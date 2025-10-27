package com.gengzi.enums;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public enum FileProcessStatusEnum {

    // 枚举常量示例
    UN_PROCESSED(0, "未处理"),
    PROCESSING(1, "处理中"),
    PROCESS_SUCCESS(2, "处理完成"),
    PROCESS_FAILED(3, "处理失败");

    // 枚举对应的代码值
    private final int code;

    // 枚举的描述信息
    private final String description;

    // 缓存映射，用于快速查找
    private static final Map<Integer, FileProcessStatusEnum> CODE_MAP = new HashMap<>();

    // 静态初始化块，初始化缓存
    static {
        for (FileProcessStatusEnum enumInstance : values()) {
            CODE_MAP.put(enumInstance.code, enumInstance);
        }
    }

    // 私有构造函数，确保枚举只能内部定义
    FileProcessStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    // Getter方法
    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据代码值获取枚举实例
     * @param code 代码值
     * @return 对应的枚举实例，不存在则返回Optional.empty()
     */
    public static Optional<FileProcessStatusEnum> fromCode(int code) {
        return Optional.ofNullable(CODE_MAP.get(code));
    }

    /**
     * 检查当前枚举是否为指定实例
     * @param enumInstance 要比较的枚举实例
     * @return 如果相同返回true，否则返回false
     */
    public boolean is(FileProcessStatusEnum enumInstance) {
        return this == enumInstance;
    }

    /**
     * 检查当前枚举是否在指定的枚举列表中
     * @param enumInstances 枚举列表
     * @return 如果在列表中返回true，否则返回false
     */
    public boolean in(FileProcessStatusEnum... enumInstances) {
        if (enumInstances == null || enumInstances.length == 0) {
            return false;
        }
        for (FileProcessStatusEnum instance : enumInstances) {
            if (this == instance) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("%s{code=%d, description='%s'}",
                this.name(), code, description);
    }
}

package com.gengzi.enums;

/**
 * 标记 Markdown 内容块的类型枚举
 */
public enum BlockType {
    // 枚举常量：图片、表格、代码块、文本
    IMAGE("image"),
    TABLE("table"),
    CODE("code"),
    TEXT("text");

    // 枚举对应的字符串标识（与外部标记一致）
    private final String type;

    /**
     * 构造函数：初始化枚举的类型标识
     * @param type 类型字符串（如 "image"）
     */
    BlockType(String type) {
        this.type = type;
    }

    /**
     * 获取枚举对应的类型字符串
     * @return 类型标识（如 "table"）
     */
    public String getType() {
        return type;
    }

    /**
     * 根据类型字符串反向获取枚举实例（忽略大小写）
     * @param type 类型字符串（如 "Image"、"CODE"）
     * @return 对应的枚举实例，若未匹配则返回 null
     */
    public static BlockType fromType(String type) {
        if (type == null) {
            return null;
        }
        for (BlockType blockType : values()) {
            if (blockType.type.equalsIgnoreCase(type)) {
                return blockType;
            }
        }
        return null;
    }

    /**
     * 判断当前枚举是否为指定类型
     * @param type 类型字符串
     * @return 若匹配则返回 true，否则返回 false
     */
    public boolean isType(String type) {
        return this.type.equalsIgnoreCase(type);
    }
}
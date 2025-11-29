package com.gengzi.rag.util;

/**
 * 工具类：用于清理 LLM（大语言模型）响应中常见的 Markdown 代码块包裹，
 * 特别是 ```json ... ``` 或 ``` ... ``` 格式，提取内部原始内容。
 *
 * <p>示例输入：
 * <pre>
 * ```json
 * { "name": "Alice" }
 * ```
 * </pre>
 *
 * <p>输出：
 * <pre>
 * { "name": "Alice" }
 * </pre>
 */
public final class CodeBlockCleaner {

    private CodeBlockCleaner() {
        // 私有构造函数，防止实例化
    }

    /**
     * 清理输入字符串中的 Markdown 代码块标记（如 ```json ... ``` 或 ``` ... ```），
     * 返回内部的原始内容。如果输入不包含代码块标记，则原样返回（去除首尾空白）。
     *
     * @param input 可能包含 Markdown 代码块的字符串，可为 null
     * @return 清理后的字符串；若输入为 null，返回 null
     */
    public static String cleanCodeBlock(String input) {
        if (input == null) {
            return null;
        }

        String trimmed = input.trim();
        if (trimmed.length() < 6) { // 最短有效代码块: ```\n```
            return trimmed;
        }

        // 检查是否以 ``` 开头且以 ``` 结尾
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            // 查找第一个换行符，确定代码块标识结束位置（如 "```json\n"）
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline == -1) {
                // 没有换行符，例如：```content```（非法但容错）
                return "";
            }

            // 内容起始位置：第一个换行符之后
            int contentStart = firstNewline + 1;
            // 内容结束位置：倒数第3个字符之前（因为结尾是 ```）
            int contentEnd = trimmed.length() - 3;

            if (contentStart <= contentEnd) {
                return trimmed.substring(contentStart, contentEnd).trim();
            } else {
                return ""; // 空内容
            }
        }

        // 不是代码块格式，直接返回去空格后的原字符串
        return trimmed;
    }

    /**
     * 专门用于清理 JSON 代码块。与 {@link #cleanCodeBlock(String)} 行为一致，
     * 但语义更明确。底层调用通用方法。
     *
     * @param jsonWithMarkdown 可能被 ```json ... ``` 包裹的 JSON 字符串
     * @return 纯净的 JSON 字符串
     */
    public static String cleanJsonBlock(String jsonWithMarkdown) {
        return cleanCodeBlock(jsonWithMarkdown);
    }
}
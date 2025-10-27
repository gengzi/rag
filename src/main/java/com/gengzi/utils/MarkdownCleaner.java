package com.gengzi.utils;

import java.util.regex.Pattern;

/**
 * Markdown文本格式清洗工具类
 * 功能：移除MD中的格式符号（#、*、_、>、`等），保留纯文本内容
 */
public class MarkdownCleaner {

    // 正则表达式预编译（提升性能）
    // 1. 移除标题符号（#及后续空格）
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+", Pattern.MULTILINE);
    // 2. 移除加粗/斜体符号（**、*、__、_）
    private static final Pattern BOLD_ITALIC_PATTERN = Pattern.compile("\\*\\*|\\*|__|_");
    // 3. 移除引用符号（>及后续空格）
    private static final Pattern QUOTE_PATTERN = Pattern.compile("^>\\s+", Pattern.MULTILINE);
    // 4. 移除代码块标记（```及语言标识）
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```.*?```", Pattern.DOTALL);
    // 5. 移除行内代码标记（`）
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`");
    // 6. 移除列表符号（-、*、数字.及后续空格）
    private static final Pattern LIST_PATTERN = Pattern.compile("^(-|\\*|\\d+\\.)\\s+", Pattern.MULTILINE);
    // 7. 移除多余空行（连续换行保留一个）
    private static final Pattern EMPTY_LINE_PATTERN = Pattern.compile("\\n{2,}", Pattern.MULTILINE);

    /**
     * 清洗MD文本格式
     *
     * @param mdText 原始Markdown文本
     * @return 清洗后的纯文本
     */
    public static String clean(String mdText) {
        if (mdText == null || mdText.isEmpty()) {
            return "";
        }

        String cleaned = mdText;

        // 逐步移除各类格式符号
        cleaned = HEADING_PATTERN.matcher(cleaned).replaceAll(""); // 处理标题
        cleaned = BOLD_ITALIC_PATTERN.matcher(cleaned).replaceAll(""); // 处理加粗/斜体
        cleaned = QUOTE_PATTERN.matcher(cleaned).replaceAll(""); // 处理引用
        cleaned = CODE_BLOCK_PATTERN.matcher(cleaned).replaceAll(matcher -> {
            // 移除代码块标记，但保留代码内容
            String codeContent = matcher.group().replace("```", "").trim();
            return codeContent + "\n"; // 代码块内容后加换行，避免与其他内容粘连
        });
        cleaned = INLINE_CODE_PATTERN.matcher(cleaned).replaceAll(""); // 处理行内代码
        cleaned = LIST_PATTERN.matcher(cleaned).replaceAll(""); // 处理列表
        cleaned = EMPTY_LINE_PATTERN.matcher(cleaned).replaceAll("\n"); // 合并空行

        // 去除首尾空白
        return cleaned.trim();
    }
}

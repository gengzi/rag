package com.gengzi.utils;

import java.util.regex.Pattern;

public class PunctuationAndLineBreakRemover {
    // 正则：匹配 标点符号(\p{P}) + 特殊符号(\p{S}) + 所有空白字符(\s)
    private static final Pattern PUNCTUATION_LINE_BREAK_PATTERN = Pattern.compile(
        "[\\p{P}\\p{S}\\s]",  // 关键：添加 \s 匹配所有空白（含换行）
        Pattern.UNICODE_CHARACTER_CLASS
    );

    /**
     * 移除标点符号 + 所有空白（含换行、空格、制表符）
     * @param text 原始文本
     * @return 处理后的紧凑文本
     */
    public static String removePunctuationAndAllWhitespace(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return PUNCTUATION_LINE_BREAK_PATTERN.matcher(text).replaceAll("");
    }

    // 示例
    public static void main(String[] args) {
        String originalText = "Hello！这是第一行文本，\n包含数字123和符号@#。\r\n这是第二行文本...";
        String cleanedText = removePunctuationAndAllWhitespace(originalText);
        
        System.out.println("原始文本（含换行）：");
        System.out.println(originalText);
        System.out.println("\n处理后（无标点+无换行）：");
        System.out.println(cleanedText); 
        // 输出："Hello这是第一行文本包含数字123和符号这是第二行文本"
    }
}
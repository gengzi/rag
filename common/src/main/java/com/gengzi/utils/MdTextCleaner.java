package com.gengzi.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MD格式文本清洗工具类：过滤无需朗读的内容，处理特殊字符，避免PaddleSpeech异常
 */
public class MdTextCleaner {

    // 1. 定义需要过滤的MD元素正则（按优先级排序）
    // 匹配MD代码块（```开头结尾、`单行代码`）
    private static final Pattern MD_CODE_BLOCK = Pattern.compile("```[\\s\\S]*?```|`[^`]+`");
    // 匹配MD图片链接（![描述](链接)）
    private static final Pattern MD_IMAGE = Pattern.compile("!\\[.*?\\]\\(.*?\\)");
    // 匹配MD超链接（[描述](链接)，保留描述文本，移除链接）
    private static final Pattern MD_LINK = Pattern.compile("\\[(.*?)\\]\\(.*?\\)");
    // 匹配MD标题符号（#、## 等，保留标题文本）
    private static final Pattern MD_HEADER = Pattern.compile("^#{1,6}\\s+|\\s+#{1,6}$", Pattern.MULTILINE);
    // 匹配MD列表符号（-、*、1. 等，保留列表内容）
    private static final Pattern MD_LIST = Pattern.compile("^\\s*[-*+]\\s+|^\\s*\\d+\\.\\s+", Pattern.MULTILINE);
    // 匹配HTML标签（如<br>、<div>等，MD中可能嵌套HTML）
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    // 匹配特殊符号（连续非文字字符，保留单个中文标点）
    private static final Pattern SPECIAL_SYMBOLS = Pattern.compile("[^a-zA-Z0-9\\u4e00-\\u9fa5，。！？；：“”‘’（）【】、\\s]+");
    // 匹配多余空格/换行（压缩为单个空格）
    private static final Pattern EXTRA_SPACE = Pattern.compile("\\s+|\\n|\\r");

    /**
     * 核心清洗方法：处理MD格式文本，返回可朗读的干净文本
     *
     * @param mdText 原始MD格式文本（可能含代码、图片、特殊符号等）
     * @return 清洗后的可朗读文本
     */
    public static String cleanMdText(String mdText) {
        // 空值处理：避免空指针
        if (StringUtils.isBlank(mdText)) {
            return "";
        }

        String cleanedText = mdText;

        // 2. 分步执行清洗（按正则优先级，先过滤大块元素，再处理细节）
        // 步骤1：过滤MD代码块（无需朗读代码）
        cleanedText = replaceAll(cleanedText, MD_CODE_BLOCK, "");
        // 步骤2：过滤MD图片（图片无文本可朗读）
        cleanedText = replaceAll(cleanedText, MD_IMAGE, "");
        // 步骤3：处理MD超链接（保留描述文本，移除链接）
        cleanedText = replaceAll(cleanedText, MD_LINK, "$1");
        // 步骤4：处理MD标题（移除#符号，保留标题文本）
        cleanedText = replaceAll(cleanedText, MD_HEADER, "");
        // 步骤5：处理MD列表（移除列表符号，保留列表内容）
        cleanedText = replaceAll(cleanedText, MD_LIST, "");
        // 步骤6：过滤HTML标签（MD中可能嵌套的HTML元素）
        cleanedText = replaceAll(cleanedText, HTML_TAG, "");
        // 步骤7：过滤特殊符号（保留中英文、数字、常用中文标点）
        cleanedText = replaceAll(cleanedText, SPECIAL_SYMBOLS, "");
        // 步骤8：压缩多余空格/换行（避免PaddleSpeech处理时的空字符异常）
        cleanedText = replaceAll(cleanedText, EXTRA_SPACE, " ");
        // 步骤9：首尾空格修剪
        cleanedText = StringUtils.trim(cleanedText);

        // 3. 处理PaddleSpeech变调异常场景（避免“一”结尾导致索引越界）
        cleanedText = handleToneSandhiException(cleanedText);

        return cleanedText;
    }

    /**
     * 处理PaddleSpeech变调异常：避免“一”结尾导致的IndexError
     *
     * @param text 清洗后的文本
     * @return 处理后的文本
     */
    private static String handleToneSandhiException(String text) {
        if (StringUtils.isBlank(text)) {
            return text;
        }

        // 场景1：文本以“一”结尾（如“测试一”），末尾加中文句号（不影响朗读语义）
        if (text.endsWith("一")) {
            text += "。";
        }

        // 场景2：文本中“一”后接特殊字符（如“一-二”），添加空格分隔（避免拼音处理异常）
        text = text.replaceAll("一([^a-zA-Z0-9\\u4e00-\\u9fa5])", "一 $1");

        return text;
    }

    /**
     * 工具方法：安全执行正则替换（避免NullPointerException）
     *
     * @param text        原始文本
     * @param pattern     正则表达式
     * @param replacement 替换内容
     * @return 替换后的文本
     */
    private static String replaceAll(String text, Pattern pattern, String replacement) {
        if (StringUtils.isBlank(text) || pattern == null) {
            return text;
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.replaceAll(replacement);
    }
}

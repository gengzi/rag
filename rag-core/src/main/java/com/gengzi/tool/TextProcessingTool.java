package com.gengzi.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文本处理工具 - 提供文本分析、摘要和处理功能
 */
@Component
public class TextProcessingTool {

    @Tool(description = "统计文本的字数、字符数、行数等信息")
    public String analyzeText(String text) {
        if (text == null || text.isEmpty()) {
            return "文本为空";
        }

        String[] lines = text.split("\n");
        String[] words = text.trim().split("\\s+");
        int charCount = text.length();
        int charCountNoSpaces = text.replaceAll("\\s", "").length();
        int wordCount = text.trim().isEmpty() ? 0 : words.length;
        int lineCount = lines.length;

        StringBuilder result = new StringBuilder();
        result.append("文本统计信息:\n");
        result.append("字符数（含空格）: ").append(charCount).append("\n");
        result.append("字符数（不含空格）: ").append(charCountNoSpaces).append("\n");
        result.append("词数: ").append(wordCount).append("\n");
        result.append("行数: ").append(lineCount).append("\n");

        return result.toString();
    }

    @Tool(description = "提取文本中的关键词（基于词频）")
    public String extractKeywords(String text, int topN) {
        if (text == null || text.isEmpty()) {
            return "文本为空";
        }

        if (topN <= 0) {
            topN = 5;
        }

        // 简单的分词和词频统计
        String[] words = text.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\s\\u4e00-\\u9fa5]", " ")
                .trim()
                .split("\\s+");

        // 过滤停用词（简化版）
        Set<String> stopWords = new HashSet<>(Arrays.asList(
                "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去",
                "你", "会", "着", "没有", "看",
                "the", "is", "at", "which", "on", "a", "an", "as", "are", "was", "were", "be", "been", "being", "in",
                "of", "to", "and", "or"));

        Map<String, Integer> wordFreq = new HashMap<>();
        for (String word : words) {
            if (word.length() > 1 && !stopWords.contains(word)) {
                wordFreq.put(word, wordFreq.getOrDefault(word, 0) + 1);
            }
        }

        // 排序并取top N
        List<Map.Entry<String, Integer>> sortedWords = wordFreq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .collect(Collectors.toList());

        if (sortedWords.isEmpty()) {
            return "未找到关键词";
        }

        return "关键词（Top " + topN + "）:\n" +
                sortedWords.stream()
                        .map(entry -> entry.getKey() + " (" + entry.getValue() + "次)")
                        .collect(Collectors.joining("\n"));
    }

    @Tool(description = "生成文本摘要（提取前几句）")
    public String summarizeText(String text, int sentenceCount) {
        if (text == null || text.isEmpty()) {
            return "文本为空";
        }

        if (sentenceCount <= 0) {
            sentenceCount = 3;
        }

        // 简单按句号分割
        String[] sentences = text.split("[。！？.!?]+");

        int count = Math.min(sentenceCount, sentences.length);
        StringBuilder summary = new StringBuilder("文本摘要（前 " + count + " 句）:\n\n");

        for (int i = 0; i < count; i++) {
            String sentence = sentences[i].trim();
            if (!sentence.isEmpty()) {
                summary.append(sentence).append("。\n");
            }
        }

        return summary.toString();
    }

    @Tool(description = "转换文本大小写。支持: uppercase(大写), lowercase(小写), capitalize(首字母大写)")
    public String convertCase(String text, String caseType) {
        if (text == null || text.isEmpty()) {
            return "文本为空";
        }

        return switch (caseType.toLowerCase()) {
            case "uppercase" -> text.toUpperCase();
            case "lowercase" -> text.toLowerCase();
            case "capitalize" -> Arrays.stream(text.split("\\s+"))
                    .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                    .collect(Collectors.joining(" "));
            default -> "不支持的转换类型。请使用: uppercase, lowercase, capitalize";
        };
    }

    @Tool(description = "查找文本中是否包含指定关键词，返回出现次数和位置")
    public String findKeyword(String text, String keyword) {
        if (text == null || text.isEmpty()) {
            return "文本为空";
        }
        if (keyword == null || keyword.isEmpty()) {
            return "关键词为空";
        }

        int count = 0;
        int index = 0;
        List<Integer> positions = new ArrayList<>();

        while ((index = text.indexOf(keyword, index)) != -1) {
            count++;
            positions.add(index);
            index += keyword.length();
        }

        if (count == 0) {
            return "未找到关键词: " + keyword;
        }

        return String.format("关键词 '%s' 出现 %d 次\n位置: %s",
                keyword, count, positions.toString());
    }
}

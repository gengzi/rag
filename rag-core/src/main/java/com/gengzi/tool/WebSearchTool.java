package com.gengzi.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 网络搜索工具 - 当知识库无法回答问题时，从互联网获取信息
 */
@Component
public class WebSearchTool {

    @Value("${web.search.api.key:}")
    private String apiKey;

    @Value("${web.search.engine:duckduckgo}")
    private String searchEngine;

    @Tool(description = "在互联网上搜索信息。当知识库中没有相关信息时使用。返回搜索结果摘要。")
    public String searchWeb(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "搜索查询不能为空";
        }

        try {
            // 使用 DuckDuckGo Instant Answer API（免费，无需API key）
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String urlString = "https://api.duckduckgo.com/?q=" + encodedQuery
                    + "&format=json&no_html=1&skip_disambig=1";

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return "网络搜索失败，HTTP状态码: " + responseCode;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // 简单解析 JSON 响应
            String jsonResponse = response.toString();

            // 提取摘要信息
            String abstractText = extractJsonField(jsonResponse, "AbstractText");
            String abstractSource = extractJsonField(jsonResponse, "AbstractSource");
            String abstractUrl = extractJsonField(jsonResponse, "AbstractURL");

            if (abstractText != null && !abstractText.isEmpty()) {
                StringBuilder result = new StringBuilder();
                result.append("搜索结果:\n\n");
                result.append(abstractText);
                if (abstractSource != null && !abstractSource.isEmpty()) {
                    result.append("\n\n来源: ").append(abstractSource);
                }
                if (abstractUrl != null && !abstractUrl.isEmpty()) {
                    result.append("\n链接: ").append(abstractUrl);
                }
                return result.toString();
            } else {
                return "未找到相关搜索结果。建议换个关键词重试。";
            }

        } catch (Exception e) {
            return "网络搜索出错: " + e.getMessage() + "。可能是网络连接问题。";
        }
    }

    @Tool(description = "获取网页内容摘要。提供URL，返回网页的简要内容。")
    public String fetchWebPageSummary(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();

            int lineCount = 0;
            while ((inputLine = in.readLine()) != null && lineCount < 50) {
                content.append(inputLine).append("\n");
                lineCount++;
            }
            in.close();

            // 简单清理HTML标签
            String text = content.toString().replaceAll("<[^>]*>", "").trim();

            if (text.length() > 1000) {
                text = text.substring(0, 1000) + "...";
            }

            return "网页内容摘要:\n" + text;

        } catch (Exception e) {
            return "获取网页内容失败: " + e.getMessage();
        }
    }

    /**
     * 简单的JSON字段提取（避免引入JSON库依赖）
     */
    private String extractJsonField(String json, String fieldName) {
        String searchPattern = "\"" + fieldName + "\":\"";
        int startIndex = json.indexOf(searchPattern);
        if (startIndex == -1) {
            return null;
        }
        startIndex += searchPattern.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) {
            return null;
        }
        return json.substring(startIndex, endIndex);
    }
}

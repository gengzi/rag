package com.gengzi.rag.embedding.load.common;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 表格表头智能提取器（通用版）
 * 使用 LLM 分析 CSV/Excel 等表格数据的前几行，智能识别表头所在行及列名
 * 适用于 CSV、Excel 等所有表格数据
 *
 * @author: gengzi
 */
@Component
public class TableHeaderExtractor {

    private static final Logger logger = LoggerFactory.getLogger(TableHeaderExtractor.class);

    @Autowired
    @Qualifier("deepseekChatClientNoRag")
    private ChatClient chatClient;

    /**
     * 表头提取结果
     */
    public static class HeaderInfo {
        private int headerRowIndex; // 表头所在行索引 (0-based)
        private List<String> columnNames; // 提取的列名列表
        private boolean success; // 是否成功提取
        private String errorMessage; // 错误消息

        public HeaderInfo(int headerRowIndex, List<String> columnNames) {
            this.headerRowIndex = headerRowIndex;
            this.columnNames = columnNames;
            this.success = true;
        }

        public HeaderInfo(String errorMessage) {
            this.success = false;
            this.errorMessage = errorMessage;
            this.headerRowIndex = -1;
            this.columnNames = new ArrayList<>();
        }

        public int getHeaderRowIndex() {
            return headerRowIndex;
        }

        public List<String> getColumnNames() {
            return columnNames;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * 智能提取表头信息
     *
     * @param firstNRows 表格数据的前 N 行（建议 5-10 行）
     * @param sourceType 数据源类型（如 "CSV"、"Excel Sheet"），用于日志
     * @return 表头信息，包含表头所在行和列名列表
     */
    public HeaderInfo extractHeaderInfo(List<List<String>> firstNRows, String sourceType) {
        if (CollUtil.isEmpty(firstNRows)) {
            return new HeaderInfo("输入数据为空");
        }

        try {
            // 构建 Prompt
            String tablePreview = buildTablePreview(firstNRows);
            String prompt = buildPrompt(tablePreview, sourceType);

            logger.info("使用 LLM 分析 {} 表头，前 {} 行数据", sourceType, firstNRows.size());

            // 调用 LLM
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            logger.debug("LLM 响应: {}", response);

            // 解析 LLM 返回的 JSON
            return parseResponse(response, firstNRows, sourceType);

        } catch (Exception e) {
            logger.error("LLM 表头提取失败: {}", sourceType, e);
            return new HeaderInfo("LLM 调用异常: " + e.getMessage());
        }
    }

    /**
     * 构建表格预览文本
     */
    private String buildTablePreview(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            sb.append("行").append(i).append(": ");
            sb.append(rows.get(i).stream()
                    .map(cell -> "\"" + (cell == null ? "" : cell) + "\"")
                    .collect(Collectors.joining(", ")));
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 构建 LLM Prompt
     */
    private String buildPrompt(String tablePreview, String sourceType) {
        return """
                你是一个表格数据分析专家。我会给你一个 %s 的前几行数据，请帮我分析：

                1. 哪一行是真正的表头（列名）？有些表格前面可能有标题、说明等元数据行。
                2. 表头中包含哪些列名？

                表格数据如下：
                ```
                %s
                ```

                请以 JSON 格式返回结果，格式如下：
                {
                  "header_row_index": 0,
                  "column_names": ["列名1", "列名2", "列名3"],
                  "reasoning": "简要说明判断依据"
                }

                注意：
                - header_row_index 是从 0 开始的行索引
                - 如果第一行就是表头，返回 0
                - 如果第二行是表头，返回 1，以此类推
                - column_names 必须按顺序列出所有列名
                - 只返回 JSON，不要有其他文字
                """.formatted(sourceType, tablePreview);
    }

    /**
     * 解析 LLM 响应
     */
    private HeaderInfo parseResponse(String response, List<List<String>> originalRows, String sourceType) {
        try {
            // 提取 JSON 部分（去除可能的 markdown 代码块标记）
            String jsonStr = response.trim();
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substring(7);
            }
            if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substring(3);
            }
            if (jsonStr.endsWith("```")) {
                jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
            }
            jsonStr = jsonStr.trim();

            JSONObject json = JSONUtil.parseObj(jsonStr);

            int headerRowIndex = json.getInt("header_row_index", 0);
            List<String> columnNames = json.getBeanList("column_names", String.class);
            String reasoning = json.getStr("reasoning", "");

            // 验证结果
            if (headerRowIndex < 0 || headerRowIndex >= originalRows.size()) {
                logger.warn("LLM 返回的行索引超出范围: {}, 使用第一行作为表头", headerRowIndex);
                headerRowIndex = 0;
            }

            if (CollUtil.isEmpty(columnNames)) {
                logger.warn("LLM 未返回列名，使用检测到的表头行数据");
                columnNames = originalRows.get(headerRowIndex);
            }

            logger.info("{} LLM 分析结果: 表头在第 {} 行, 列数: {}, 理由: {}",
                    sourceType, headerRowIndex, columnNames.size(), reasoning);

            return new HeaderInfo(headerRowIndex, columnNames);

        } catch (Exception e) {
            logger.error("解析 LLM 响应失败 ({}): {}", sourceType, response, e);
            return new HeaderInfo("解析 LLM 响应失败: " + e.getMessage());
        }
    }

    /**
     * 从原始行数据中提取表头和数据行
     *
     * @param allRows    所有行数据
     * @param headerInfo LLM 提取的表头信息
     * @return Map 包含 "headers" 和 "dataRows"
     */
    public Map<String, Object> splitHeaderAndData(List<List<String>> allRows, HeaderInfo headerInfo) {
        Map<String, Object> result = new HashMap<>();

        if (!headerInfo.isSuccess() || CollUtil.isEmpty(allRows)) {
            result.put("headers", new ArrayList<String>());
            result.put("dataRows", allRows);
            return result;
        }

        int headerIdx = headerInfo.getHeaderRowIndex();

        // 表头
        result.put("headers", headerInfo.getColumnNames());

        // 数据行（跳过表头及之前的所有行）
        List<List<String>> dataRows = new ArrayList<>();
        for (int i = headerIdx + 1; i < allRows.size(); i++) {
            dataRows.add(allRows.get(i));
        }
        result.put("dataRows", dataRows);

        return result;
    }
}

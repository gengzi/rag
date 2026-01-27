package com.gengzi.rag.embedding.load.csv;

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
 * CSV表头智能提取器
 * 使用LLM分析CSV前几行，智能识别表头所在行及列名
 *
 * @author: gengzi
 */
@Component
public class CsvHeaderExtractor {

    private static final Logger logger = LoggerFactory.getLogger(CsvHeaderExtractor.class);

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
     * 智能提取CSV表头信息
     *
     * @param firstNRows CSV文件的前N行数据（建议5-10行）
     * @return 表头信息，包含表头所在行和列名列表
     */
    public HeaderInfo extractHeaderInfo(List<List<String>> firstNRows) {
        if (CollUtil.isEmpty(firstNRows)) {
            return new HeaderInfo("输入数据为空");
        }

        try {
            // 构建Prompt
            String csvPreview = buildCsvPreview(firstNRows);
            String prompt = buildPrompt(csvPreview);

            logger.info("使用LLM分析CSV表头，前{}行数据", firstNRows.size());

            // 调用LLM
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            logger.debug("LLM响应: {}", response);

            // 解析LLM返回的JSON
            return parseResponse(response, firstNRows);

        } catch (Exception e) {
            logger.error("LLM表头提取失败", e);
            return new HeaderInfo("LLM调用异常: " + e.getMessage());
        }
    }

    /**
     * 构建CSV预览文本
     */
    private String buildCsvPreview(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            sb.append("行").append(i).append(": ");
            sb.append(rows.get(i).stream()
                    .map(cell -> "\"" + cell + "\"")
                    .collect(Collectors.joining(", ")));
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 构建LLM Prompt
     */
    private String buildPrompt(String csvPreview) {
        return """
                你是一个CSV文件分析专家。我会给你一个CSV文件的前几行数据，请帮我分析：

                1. 哪一行是真正的表头（列名）？有些CSV文件前面可能有标题、说明等元数据行。
                2. 表头中包含哪些列名？

                CSV数据如下：
                ```
                %s
                ```

                请以JSON格式返回结果，格式如下：
                {
                  "header_row_index": 0,
                  "column_names": ["列名1", "列名2", "列名3"],
                  "reasoning": "简要说明判断依据"
                }

                注意：
                - header_row_index 是从0开始的行索引
                - 如果第一行就是表头，返回0
                - 如果第二行是表头，返回1，以此类推
                - column_names 必须按顺序列出所有列名
                - 只返回JSON，不要有其他文字
                """.formatted(csvPreview);
    }

    /**
     * 解析LLM响应
     */
    private HeaderInfo parseResponse(String response, List<List<String>> originalRows) {
        try {
            // 提取JSON部分（去除可能的markdown代码块标记）
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
                logger.warn("LLM返回的行索引超出范围: {}, 使用第一行作为表头", headerRowIndex);
                headerRowIndex = 0;
            }

            if (CollUtil.isEmpty(columnNames)) {
                logger.warn("LLM未返回列名，使用检测到的表头行数据");
                columnNames = originalRows.get(headerRowIndex);
            }

            logger.info("LLM分析结果: 表头在第{}行, 列数: {}, 理由: {}",
                    headerRowIndex, columnNames.size(), reasoning);

            return new HeaderInfo(headerRowIndex, columnNames);

        } catch (Exception e) {
            logger.error("解析LLM响应失败: {}", response, e);
            return new HeaderInfo("解析LLM响应失败: " + e.getMessage());
        }
    }

    /**
     * 从原始行数据中提取表头和数据行
     * 
     * @param allRows    所有行数据
     * @param headerInfo LLM提取的表头信息
     * @return Map包含 "headers" 和 "dataRows"
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

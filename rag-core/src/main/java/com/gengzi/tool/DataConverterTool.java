package com.gengzi.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 数据转换工具 - 在不同数据格式之间转换
 */
@Component
public class DataConverterTool {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(description = "格式化JSON字符串，使其更易读")
    public String formatJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return "JSON字符串为空";
        }

        try {
            Object json = objectMapper.readValue(jsonString, Object.class);
            return "格式化后的JSON:\n" + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception e) {
            return "JSON格式化失败: " + e.getMessage();
        }
    }

    @Tool(description = "验证JSON字符串是否有效")
    public String validateJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return "JSON字符串为空";
        }

        try {
            objectMapper.readTree(jsonString);
            return "✓ JSON格式有效";
        } catch (Exception e) {
            return "✗ JSON格式无效: " + e.getMessage();
        }
    }

    @Tool(description = "将JSON数组转换为简单的表格格式")
    public String jsonToTable(String jsonArrayString) {
        if (jsonArrayString == null || jsonArrayString.trim().isEmpty()) {
            return "JSON数组为空";
        }

        try {
            // 解析JSON数组
            List<Map<String, Object>> data = objectMapper.readValue(
                    jsonArrayString,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            if (data.isEmpty()) {
                return "JSON数组为空";
            }

            // 获取所有键
            Map<String, Object> firstObject = data.get(0);
            StringBuilder result = new StringBuilder("表格格式:\n\n");

            // 添加表头
            result.append(String.join(" | ", firstObject.keySet())).append("\n");
            result.append("-".repeat(50)).append("\n");

            // 添加数据行
            for (Map<String, Object> row : data) {
                result.append(row.values().stream()
                        .map(String::valueOf)
                        .reduce((a, b) -> a + " | " + b)
                        .orElse(""));
                result.append("\n");
            }

            return result.toString();

        } catch (Exception e) {
            return "JSON转表格失败: " + e.getMessage();
        }
    }

    @Tool(description = "压缩JSON（移除空格和换行）")
    public String compactJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return "JSON字符串为空";
        }

        try {
            Object json = objectMapper.readValue(jsonString, Object.class);
            return objectMapper.writeValueAsString(json);
        } catch (Exception e) {
            return "JSON压缩失败: " + e.getMessage();
        }
    }

    @Tool(description = "从JSON中提取指定字段的值。使用点号表示嵌套，如: 'user.name'")
    public String extractJsonField(String jsonString, String fieldPath) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return "JSON字符串为空";
        }

        try {
            Map<String, Object> json = objectMapper.readValue(jsonString, Map.class);
            String[] fields = fieldPath.split("\\.");

            Object current = json;
            for (String field : fields) {
                if (current instanceof Map) {
                    current = ((Map<?, ?>) current).get(field);
                } else {
                    return "字段路径无效: " + fieldPath;
                }
            }

            if (current == null) {
                return "字段不存在: " + fieldPath;
            }

            return "提取的值: " + objectMapper.writeValueAsString(current);

        } catch (Exception e) {
            return "提取字段失败: " + e.getMessage();
        }
    }

    @Tool(description = "转义特殊字符使文本可以安全地放入JSON字符串中")
    public String escapeForJson(String text) {
        if (text == null) {
            return "null";
        }

        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

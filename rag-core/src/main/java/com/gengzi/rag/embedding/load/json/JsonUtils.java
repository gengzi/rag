package com.gengzi.rag.embedding.load.json;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.gengzi.rag.embedding.load.csv.CsvUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * JSON/JSONL 解析工具类
 * 使用 Hutool JSONUtil 实现高容错解析
 *
 * @author: gengzi
 */
public class JsonUtils {

    private static final Logger logger = LoggerFactory.getLogger(JsonUtils.class);

    /**
     * 解析 JSON 或 JSONL 文件内容
     * 
     * 支持格式：
     * - JSON: 对象数组 [{"key": "value"}, ...]
     * - JSONL: 行分隔的 JSON 对象
     * 
     * 容错特性：
     * - 支持单引号 {'key': 'value'}
     * - 支持无引号键 {key: value}
     * - 支持尾随逗号 {a: 1, b: 2,}
     * - 解析失败的行记录到日志但不中断处理
     * - 如果失败率超过 50% 则抛出异常
     *
     * @param content 文件字节内容
     * @return 解析后的记录列表，每条记录为 Map
     */
    public static List<Map<String, Object>> parseJson(byte[] content) {
        String jsonStr = new String(content, StandardCharsets.UTF_8).trim();

        // 先尝试判断是 JSON 数组还是 JSONL
        if (jsonStr.startsWith("[")) {
            // JSON 数组格式
            return parseJsonArray(jsonStr);
        } else {
            // JSONL 格式（逐行）
            return parseJsonLines(jsonStr);
        }
    }

    /**
     * 解析 JSON 数组格式
     */
    private static List<Map<String, Object>> parseJsonArray(String jsonStr) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            // Hutool 的 JSONUtil.parse 支持宽松模式
            JSONArray jsonArray = JSONUtil.parseArray(jsonStr);

            for (int i = 0; i < jsonArray.size(); i++) {
                try {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    result.add(obj);
                } catch (Exception e) {
                    logger.warn("解析 JSON 数组第 {} 项失败: {}", i, e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("解析 JSON 数组失败: {}", e.getMessage());
            throw new RuntimeException("无法解析 JSON 数组: " + e.getMessage(), e);
        }

        return result;
    }

    /**
     * 解析 JSONL 格式（每行一个 JSON 对象）
     */
    private static List<Map<String, Object>> parseJsonLines(String content) {
        List<Map<String, Object>> result = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");

        int totalLines = 0;
        int failedLines = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // 跳过空行
            if (StrUtil.isBlank(line)) {
                continue;
            }

            totalLines++;

            try {
                // Hutool 的 parseObj 支持宽松模式
                JSONObject obj = JSONUtil.parseObj(line);
                result.add(obj);
            } catch (Exception e) {
                failedLines++;
                logger.warn("解析 JSONL 第 {} 行失败（原始内容: {}）: {}",
                        i + 1,
                        line.length() > 100 ? line.substring(0, 100) + "..." : line,
                        e.getMessage());
            }
        }

        // 如果失败率超过 50%，认为文件格式有严重问题
        if (totalLines > 0 && (double) failedLines / totalLines > 0.5) {
            String errorMsg = String.format(
                    "JSONL 解析失败率过高: %d/%d (%.1f%%), 请检查文件格式",
                    failedLines, totalLines, (double) failedLines / totalLines * 100);
            logger.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }

        if (failedLines > 0) {
            logger.info("JSONL 解析完成，成功: {}, 失败: {} ({}%)",
                    result.size(), failedLines,
                    String.format("%.1f", (double) failedLines / totalLines * 100));
        }

        return result;
    }

    /**
     * 提取所有唯一的键（字段名）
     * 
     * @param rows 所有记录
     * @return 所有出现过的键的列表（有序）
     */
    public static List<String> extractAllKeys(List<Map<String, Object>> rows) {
        Set<String> keySet = new LinkedHashSet<>();

        for (Map<String, Object> row : rows) {
            keySet.addAll(row.keySet());
        }

        return new ArrayList<>(keySet);
    }

    /**
     * 推断 Schema（字段类型）
     * 
     * @param rows 所有记录
     * @param keys 所有字段名
     * @return 字段名 -> 类型映射
     */
    public static Map<String, String> inferSchema(List<Map<String, Object>> rows, List<String> keys) {
        Map<String, String> schemaMap = new HashMap<>();

        for (String key : keys) {
            // 收集该字段的样本值（转为 String）
            List<String> samples = new ArrayList<>();

            for (Map<String, Object> row : rows) {
                Object value = row.get(key);
                if (value != null) {
                    samples.add(String.valueOf(value));
                }
            }

            // 使用 CsvUtils 的类型推断逻辑
            String inferredType = CsvUtils.inferType(samples);
            schemaMap.put(key, inferredType);
        }

        return schemaMap;
    }

    /**
     * 展开嵌套对象（V1 暂时不实现，保留接口）
     * 
     * @param json 原始 JSON 对象
     * @return 展开后的扁平对象
     */
    public static Map<String, Object> flatten(Map<String, Object> json) {
        // V1 暂不实现，直接返回顶层字段
        // 嵌套对象保留为 JSON 字符串
        Map<String, Object> flattened = new HashMap<>();

        for (Map.Entry<String, Object> entry : json.entrySet()) {
            Object value = entry.getValue();

            // 如果是嵌套对象或数组，转为 JSON 字符串
            if (value instanceof Map || value instanceof List) {
                flattened.put(entry.getKey(), JSONUtil.toJsonStr(value));
            } else {
                flattened.put(entry.getKey(), value);
            }
        }

        return flattened;
    }
}

package com.gengzi.rag.embedding.load.csv;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.pinyin.PinyinUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * CSV处理工具类：列名清洗、值清洗、类型推断
 */
public class CsvUtils {

    private static final Pattern NON_WORD_CHAR = Pattern.compile("[^a-z0-9_]");
    private static final Pattern INVISIBLE_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F]");

    /**
     * 规范化列名列表
     */
    public static Map<String, String> normalizeColumns(List<String> headers) {
        Map<String, String> mapping = new HashMap<>(); // old -> new
        Set<String> usedNames = new HashSet<>();

        for (int i = 0; i < headers.size(); i++) {
            String original = headers.get(i);
            String norm = normalizeColumnName(original);

            // 处理重名
            if (usedNames.contains(norm)) {
                int index = 2;
                while (usedNames.contains(norm + "_" + index)) {
                    index++;
                }
                norm = norm + "_" + index;
            }

            usedNames.add(norm);
            mapping.put(original, norm);
        }
        return mapping;
    }

    /**
     * 单个列名规范化
     * 规则：
     * 1. Trim
     * 2. 中文转拼音
     * 3. 转小写
     * 4. 空格/特殊字符 -> _
     */
    public static String normalizeColumnName(String colName) {
        if (StrUtil.isBlank(colName)) {
            return "col";
        }

        // 1. Trim
        String temp = colName.trim();

        // 2. 中文转拼音 (Hutool)
        if (ReUtil.contains("[\\u4e00-\\u9fa5]", temp)) {
            temp = PinyinUtil.getPinyin(temp, "");
        }

        // 3. 转小写
        temp = temp.toLowerCase();

        // 4. 替换非字母数字为下划线
        // temp = NON_WORD_CHAR.matcher(temp).replaceAll("_"); // regex alternative
        temp = temp.replaceAll("[^a-z0-9]", "_");

        // 5. 去除多余下划线 (e.g. __ -> _)
        temp = temp.replaceAll("_+", "_");

        // 6. 去除首尾下划线
        temp = StrUtil.strip(temp, "_");

        if (StrUtil.isBlank(temp)) {
            return "col";
        }

        // 也就是如果首字符是数字，加个前缀，Avro/Parquet 对字段名可能有限制
        if (Character.isDigit(temp.charAt(0))) {
            temp = "col_" + temp;
        }

        return temp;
    }

    /**
     * 值清洗 - 严格类型转换策略
     * 
     * 一旦推断出目标类型，就强制将所有值转换为该类型
     * 如果无法转换，返回 null（而不是报错或返回原始字符串）
     * 这样可以确保列的类型一致性，避免混合类型问题
     * 
     * @param value      原始值
     * @param targetType 目标类型 (DOUBLE, LONG, TIMESTAMP, STRING)
     * @return 转换后的值，或 null
     */
    public static Object cleanValue(String value, String targetType) {
        if (value == null)
            return null;

        // 去不可见字符 & Trim
        String clean = INVISIBLE_CHARS.matcher(value).replaceAll("").trim();

        // 空值归一
        if (StrUtil.isBlank(clean) || "NA".equalsIgnoreCase(clean) || "null".equalsIgnoreCase(clean)
                || "-".equals(clean)) {
            return null;
        }

        // 根据目标类型进行严格转换
        switch (targetType) {
            case "DOUBLE":
                return convertToDouble(clean);

            case "LONG":
                return convertToLong(clean);

            case "TIMESTAMP":
                return convertToTimestamp(clean);

            case "STRING":
            default:
                return clean;
        }
    }

    /**
     * 严格转换为 DOUBLE
     * 尝试多种数值格式，失败返回 null
     */
    private static Object convertToDouble(String value) {
        try {
            // 清洗数值：去除千分位、货币符号、百分号
            String numStr = value.replace(",", "").replace("￥", "").replace("$", "");

            // 处理百分比
            if (numStr.endsWith("%")) {
                double v = Double.parseDouble(numStr.substring(0, numStr.length() - 1));
                return v / 100.0;
            }

            // 标准数值转换
            if (NumberUtil.isNumber(numStr)) {
                return Double.parseDouble(numStr);
            }
        } catch (Exception e) {
            // 转换失败，忽略
        }
        return null; // 无法转换为 DOUBLE，返回 null
    }

    /**
     * 严格转换为 LONG
     * 只接受整数，失败返回 null
     */
    private static Object convertToLong(String value) {
        try {
            // 清洗数值
            String numStr = value.replace(",", "").replace("￥", "").replace("$", "");

            // 必须是整数（不能有小数点）
            if (NumberUtil.isLong(numStr)) {
                return Long.parseLong(numStr);
            }
        } catch (Exception e) {
            // 转换失败，忽略
        }
        return null; // 无法转换为 LONG，返回 null
    }

    /**
     * 严格转换为 TIMESTAMP
     * 尝试解析多种日期格式，失败返回 null
     */
    private static Object convertToTimestamp(String value) {
        try {
            // Hutool 的 DateUtil.parse 支持多种格式自动识别
            return cn.hutool.core.date.DateUtil.parse(value).getTime();
        } catch (Exception e) {
            // 如果 Hutool 解析失败，尝试一些特殊格式
            try {
                // 尝试解析 ISO 8601 格式: 2024-01-28T12:00:00
                if (value.contains("T")) {
                    return cn.hutool.core.date.DateUtil.parseDateTime(value).getTime();
                }
                // 尝试纯日期格式: 2024-01-28
                if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    return cn.hutool.core.date.DateUtil.parseDate(value).getTime();
                }
            } catch (Exception ex) {
                // 忽略
            }
        }
        return null; // 无法转换为 TIMESTAMP，返回 null
    }

    /**
     * 简单类型推断
     */
    public static String inferType(List<String> sampleValues) {
        if (sampleValues == null || sampleValues.isEmpty())
            return "STRING";

        int stringCount = 0;
        int doubleCount = 0;
        int longCount = 0;
        int dateCount = 0;
        int validSamples = 0;

        for (String val : sampleValues) {
            if (StrUtil.isBlank(val) || "null".equalsIgnoreCase(val))
                continue;
            validSamples++;

            String v = val.trim();

            // 优先检查日期（日期比数字更具体，应该优先判断）
            boolean isDate = false;
            try {
                cn.hutool.core.date.DateUtil.parse(v);
                dateCount++;
                isDate = true;
            } catch (Exception e) {
                // 不是日期，继续检查其他类型
            }

            // 如果不是日期，再检查是否为数字
            if (!isDate) {
                String cleanNum = v.replace(",", "").replace("￥", "").replace("$", "").replace("%", "");
                if (NumberUtil.isLong(cleanNum)) {
                    longCount++;
                } else if (NumberUtil.isNumber(cleanNum)) {
                    doubleCount++;
                } else {
                    // 既不是日期也不是数字
                    stringCount++;
                }
            }
        }

        if (validSamples == 0)
            return "STRING";

        // 优先判断顺序（从严格到宽松）:
        // 1. 如果有任何纯字符串值（非数字、非日期） -> STRING
        // 2. 如果大部分是日期 -> TIMESTAMP (优先于数字类型，避免日期被误判为数字)
        // 3. 如果有浮点数 -> DOUBLE
        // 4. 否则 -> LONG

        if (stringCount > 0)
            return "STRING";

        // 日期检测优先于数字类型（避免 '1900-01-08' 被当作数字）
        // 降低阈值到 50%，因为现在优先检查日期，可以更准确地识别混合列
        if (dateCount > validSamples * 0.5) // 50% 以上是日期
            return "TIMESTAMP";

        if (doubleCount > 0)
            return "DOUBLE";

        if (longCount > 0)
            return "LONG";

        return "STRING"; // 兜底
    }
}

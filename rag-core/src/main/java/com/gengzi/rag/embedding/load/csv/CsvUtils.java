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
     * 值清洗
     * 1. 空值归一
     * 2. 去不可见字符、Trim
     * 3. 数值清洗
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

        if ("DOUBLE".equals(targetType) || "LONG".equals(targetType)) {
            // 数值清洗: 1,234 -> 1234; ￥12.3 -> 12.3; 12% -> 0.12
            String numStr = clean.replace(",", "").replace("￥", "").replace("$", "");
            if (numStr.endsWith("%")) {
                try {
                    double v = Double.parseDouble(numStr.substring(0, numStr.length() - 1));
                    return v / 100.0;
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
            if (NumberUtil.isNumber(numStr)) {
                if ("LONG".equals(targetType) && !numStr.contains(".")) {
                    try {
                        return Long.parseLong(numStr);
                    } catch (Exception e) {
                        /* ignore */}
                }
                try {
                    return Double.parseDouble(numStr);
                } catch (Exception e) {
                    /* ignore */}
            }
        }

        if ("TIMESTAMP".equals(targetType)) {
            // 简单的日期尝试解析
            try {
                // Try parsing common formats
                return cn.hutool.core.date.DateUtil.parse(clean).getTime();
            } catch (Exception e) {
                // ignore
            }
        }

        return clean;
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

            String v = val.trim().replace(",", "").replace("￥", "").replace("$", "").replace("%", "");

            boolean isNum = false;
            // Check numeric
            if (NumberUtil.isLong(v)) {
                longCount++;
                isNum = true;
            } else if (NumberUtil.isNumber(v)) {
                doubleCount++;
                isNum = true;
            }

            if (!isNum) {
                // Check Date
                try {
                    cn.hutool.core.date.DateUtil.parse(val.trim());
                    dateCount++;
                } catch (Exception e) {
                    stringCount++;
                }
            }
        }

        if (validSamples == 0)
            return "STRING";

        // 优先判断
        // 如果全是数字 -> LONG/DOUBLE
        // 如果是数字和日期 -> STRING (混杂)
        // 主要是 stringCount > 0 -> STRING (Strict)

        if (stringCount > 0)
            return "STRING";
        if (dateCount > validSamples * 0.9)
            return "TIMESTAMP"; // 90% 像是日期
        if (doubleCount > 0)
            return "DOUBLE";
        return "LONG";
    }
}

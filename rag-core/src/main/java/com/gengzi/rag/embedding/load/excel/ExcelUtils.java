package com.gengzi.rag.embedding.load.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.read.metadata.ReadSheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.*;

/**
 * Excel 解析工具类
 * 使用 EasyExcel 实现高性能解析
 *
 * @author: gengzi
 */
public class ExcelUtils {

    private static final Logger logger = LoggerFactory.getLogger(ExcelUtils.class);

    /**
     * Sheet 数据封装类
     */
    public static class SheetData {
        private String sheetName;
        private List<String> headers;
        private List<Map<String, Object>> rows;
        private List<List<String>> rawRows; // 原始行数据（用于 LLM 表头检测）

        public SheetData(String sheetName) {
            this.sheetName = sheetName;
            this.headers = new ArrayList<>();
            this.rows = new ArrayList<>();
            this.rawRows = new ArrayList<>();
        }

        public String getSheetName() {
            return sheetName;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public List<Map<String, Object>> getRows() {
            return rows;
        }

        public List<List<String>> getRawRows() {
            return rawRows;
        }

        public void setHeaders(List<String> headers) {
            this.headers = headers;
        }

        public void addRow(Map<String, Object> row) {
            this.rows.add(row);
        }

        public void addRawRow(List<String> row) {
            this.rawRows.add(row);
        }

        public void setRawRows(List<List<String>> rawRows) {
            this.rawRows = rawRows;
        }
    }

    /**
     * 解析 Excel 文件（支持 .xls 和 .xlsx）
     * 不假设第一行是表头，返回原始行数据供后续 LLM 分析
     *
     * @param content Excel 文件字节内容
     * @return 所有 sheet 的数据列表
     */
    public static List<SheetData> parseExcel(byte[] content) {
        List<SheetData> allSheets = new ArrayList<>();

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
            // 获取所有 sheet
            List<ReadSheet> readSheets = EasyExcel.read(inputStream).build().excelExecutor().sheetList();

            for (ReadSheet readSheet : readSheets) {
                String sheetName = readSheet.getSheetName();
                logger.info("开始解析 sheet: {}", sheetName);

                SheetRawDataListener listener = new SheetRawDataListener(sheetName);

                // 关键：设置 headRowNumber 为 null，表示没有表头行，读取所有原始数据
                EasyExcel.read(new ByteArrayInputStream(content), listener)
                        .sheet(readSheet.getSheetNo())
                        .headRowNumber(null) // 不跳过任何行，从第0行开始读取
                        .doRead();

                SheetData sheetData = listener.getSheetData();

                // 跳过空 sheet
                if (sheetData.getRawRows().isEmpty()) {
                    logger.warn("跳过空 sheet: {}", sheetName);
                    continue;
                }

                allSheets.add(sheetData);
                logger.info("Sheet \"{}\" 解析完成，共 {} 行原始数据", sheetName, sheetData.getRawRows().size());
            }

        } catch (Exception e) {
            logger.error("Excel 解析失败", e);
            throw new RuntimeException("Excel 解析失败: " + e.getMessage(), e);
        }

        return allSheets;
    }

    /**
     * EasyExcel 监听器（读取原始数据，不做表头假设）
     */
    private static class SheetRawDataListener extends AnalysisEventListener<Map<Integer, Object>> {
        private final String sheetName;
        private final SheetData sheetData;

        public SheetRawDataListener(String sheetName) {
            this.sheetName = sheetName;
            this.sheetData = new SheetData(sheetName);
        }

        /**
         * 这个方法会在读取表头时被调用
         * 我们把表头行也当作数据行处理
         */
        @Override
        public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
            // 将表头行也加入原始数据
            List<String> row = new ArrayList<>();
            int maxIndex = headMap.keySet().stream().max(Integer::compareTo).orElse(-1);

            for (int i = 0; i <= maxIndex; i++) {
                String value = headMap.get(i);
                row.add(value == null ? "" : value.trim());
            }

            sheetData.addRawRow(row);
            logger.debug("读取到第一行（表头行）: {}", row);
        }

        @Override
        public void invoke(Map<Integer, Object> rawRow, AnalysisContext context) {
            // 转换为 List<String>
            List<String> row = new ArrayList<>();
            int maxIndex = rawRow.keySet().stream().max(Integer::compareTo).orElse(-1);

            for (int i = 0; i <= maxIndex; i++) {
                Object value = rawRow.get(i);
                row.add(value == null ? "" : String.valueOf(value).trim());
            }

            sheetData.addRawRow(row);
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            logger.debug("Sheet \"{}\" 原始数据读取完成，共 {} 行", sheetName, sheetData.getRawRows().size());
        }

        public SheetData getSheetData() {
            return sheetData;
        }
    }

    /**
     * 规范化 sheet 名称，用作 S3 路径
     *
     * @param sheetName 原始 sheet 名称
     * @return 规范化后的名称
     */
    public static String normalizeSheetName(String sheetName) {
        if (sheetName == null || sheetName.trim().isEmpty()) {
            return "Sheet";
        }

        // 保留中文、字母、数字、下划线，其他字符替换为下划线
        String normalized = sheetName
                .replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "_")
                .replaceAll("_+", "_");

        // 去除首尾下划线
        normalized = normalized.replaceAll("^_+|_+$", "");

        return normalized.isEmpty() ? "Sheet" : normalized;
    }
}

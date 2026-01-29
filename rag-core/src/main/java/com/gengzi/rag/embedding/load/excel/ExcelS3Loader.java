package com.gengzi.rag.embedding.load.excel;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.gengzi.config.S3Properties;
import com.gengzi.context.DocumentMetadataMap;
import com.gengzi.context.FileContext;
import com.gengzi.dao.Document;
import com.gengzi.dao.repository.DocumentRepository;
import com.gengzi.enums.FileProcessStatusEnum;
import com.gengzi.enums.S3FileType;
import com.gengzi.rag.embedding.load.common.DatasetCardEnhancer;
import com.gengzi.rag.embedding.load.csv.CsvUtils;
import com.gengzi.rag.embedding.load.csv.ParquetWriterUtil;
import com.gengzi.rag.vector.es.EsVectorDocumentConverter;
import com.gengzi.s3.S3ClientUtils;
import com.gengzi.utils.FileIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Excel S3 加载器
 * 支持多 sheet 处理，每个 sheet 独立进行 ETL
 *
 * @author: gengzi
 */
@Component
public class ExcelS3Loader {

    private static final Logger logger = LoggerFactory.getLogger(ExcelS3Loader.class);

    @Autowired
    private S3Properties s3Properties;
    @Autowired
    private S3ClientUtils s3ClientUtils;
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    @Qualifier("asyncTaskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Autowired
    @Qualifier("duckdbDataSource")
    private DataSource duckdbDataSource;
    @Autowired
    private DatasetCardEnhancer datasetCardEnhancer;
    @Autowired
    private com.gengzi.rag.embedding.load.common.TableHeaderExtractor tableHeaderExtractor;

    /**
     * Excel 文件解析入口
     */
    public void excelParse(String filePath, Document document) {
        String documentId = document.getId();
        String kbId = document.getKbId();
        String defaultBucketName = s3Properties.getDefaultBucketName();

        // 获取文件元信息
        var headObjectResponse = s3ClientUtils.headObject(defaultBucketName, filePath);
        FileContext fileContext = FileContext.from(headObjectResponse, defaultBucketName, filePath, documentId, kbId);

        // 异步解析
        asyncParseExcel(fileContext);
    }

    @Transactional(rollbackFor = Exception.class)
    public CompletableFuture<?> asyncParseExcel(FileContext fileContext) {
        return CompletableFuture.runAsync(() -> {
            List<Path> tempFiles = new ArrayList<>();

            try {
                // 1. Extract: 下载并解析 Excel
                byte[] excelBytes = s3ClientUtils.getObject(fileContext.getBucketName(), fileContext.getKey());
                if (excelBytes == null || excelBytes.length == 0) {
                    logger.error("Excel 文件内容为空: {}", fileContext.getKey());
                    return;
                }

                List<ExcelUtils.SheetData> sheets = ExcelUtils.parseExcel(excelBytes);
                if (sheets.isEmpty()) {
                    logger.error("Excel 中没有有效的 sheet");
                    return;
                }

                logger.info("成功解析 Excel，共 {} 个 sheet", sheets.size());

                int totalChunks = 0;

                // 2. 对每个 sheet 进行 ETL
                for (ExcelUtils.SheetData sheet : sheets) {
                    try {
                        processSheet(sheet, fileContext, tempFiles);
                        totalChunks++;
                    } catch (Exception e) {
                        logger.error("处理 sheet \"{}\" 失败: {}", sheet.getSheetName(), e.getMessage(), e);
                    }
                }

                // 更新处理状态
                documentRepository.updateChunkNumAndStatusById(
                        fileContext.getDocumentId(),
                        totalChunks,
                        String.valueOf(FileProcessStatusEnum.PROCESS_SUCCESS.getCode()));

                logger.info("Excel 处理完成: {}, 共处理 {} 个 sheet", fileContext.getKey(), totalChunks);

            } catch (Exception e) {
                logger.error("Excel 处理异常", e);
                e.printStackTrace();
            } finally {
                deleteTempFiles(tempFiles);
            }
        }, threadPoolTaskExecutor);
    }

    /**
     * 处理单个 sheet
     */
    private void processSheet(ExcelUtils.SheetData sheet, FileContext fileContext, List<Path> tempFiles)
            throws IOException {

        String sheetName = sheet.getSheetName();
        String normalizedSheetName = ExcelUtils.normalizeSheetName(sheetName);

        logger.info("开始处理 sheet: {} (规范化名称: {})", sheetName, normalizedSheetName);

        // 使用 LLM 检测表头位置
        List<List<String>> rawRows = sheet.getRawRows();
        int previewRows = Math.min(10, rawRows.size());
        List<List<String>> firstNRows = rawRows.subList(0, previewRows);

        com.gengzi.rag.embedding.load.common.TableHeaderExtractor.HeaderInfo headerInfo = tableHeaderExtractor
                .extractHeaderInfo(firstNRows, "Excel Sheet: " + sheetName);

        List<String> originalHeaders;
        List<Map<String, Object>> dataRows;

        if (!headerInfo.isSuccess()) {
            // LLM 失败，回退到第一行作为表头
            logger.warn("LLM 表头检测失败，使用第一行作为表头: {}", headerInfo.getErrorMessage());
            originalHeaders = rawRows.isEmpty() ? new ArrayList<>() : rawRows.get(0);
            dataRows = convertToMapRows(rawRows.subList(1, rawRows.size()), originalHeaders);
        } else {
            // LLM 成功
            originalHeaders = headerInfo.getColumnNames();
            int headerRowIndex = headerInfo.getHeaderRowIndex();

            // 数据行从表头下一行开始
            List<List<String>> rawDataRows = rawRows.subList(headerRowIndex + 1, rawRows.size());
            dataRows = convertToMapRows(rawDataRows, originalHeaders);

            logger.info("LLM 检测到表头在第 {} 行，共 {} 列", headerRowIndex, originalHeaders.size());
        }

        // Transform: 规范化、类型推断
        Map<String, String> headerNormMap = CsvUtils.normalizeColumns(originalHeaders);
        List<String> normHeaders = new ArrayList<>();
        for (String header : originalHeaders) {
            normHeaders.add(headerNormMap.get(header));
        }

        // 类型推断
        Map<String, String> colTypes = inferSchemaFromMapRows(dataRows, originalHeaders);

        // 统计信息
        Map<String, Object> statsMap = analyzeStats(dataRows, originalHeaders, normHeaders);

        // Load: 写入 Parquet
        String s3Prefix = fileContext.getDocumentId() + "/" + normalizedSheetName + "/";
        Path tempParquet = writeParquetFile(fileContext, dataRows, originalHeaders, normHeaders, colTypes,
                normalizedSheetName);
        tempFiles.add(tempParquet);

        // 生成元数据
        Map<String, Object> schemaJson = generateSchemaJson(
                fileContext, s3Prefix, dataRows.size(), originalHeaders, normHeaders, colTypes, statsMap,
                sheetName);
        Path tempSchema = writeTempJsonFile(schemaJson);
        tempFiles.add(tempSchema);

        String datasetCard = generateDatasetCard(fileContext, dataRows.size(), schemaJson, dataRows,
                sheetName);
        Path tempCard = writeTempTextFile(datasetCard);
        tempFiles.add(tempCard);

        // 上传到 S3
        uploadArtifacts(fileContext.getBucketName(), s3Prefix, tempParquet, tempSchema, tempCard);

        // 存入向量库
        storeDatasetCard(datasetCard, fileContext, s3Prefix, sheetName);

        logger.info("Sheet \"{}\" 处理完成", sheetName);
    }

    /**
     * 将原始行数据转换为 Map 格式
     */
    private List<Map<String, Object>> convertToMapRows(List<List<String>> rawDataRows, List<String> headers) {
        List<Map<String, Object>> mapRows = new ArrayList<>();

        for (List<String> rawRow : rawDataRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < Math.min(headers.size(), rawRow.size()); i++) {
                row.put(headers.get(i), rawRow.get(i));
            }
            mapRows.add(row);
        }

        return mapRows;
    }

    /**
     * 从 Map 格式的行推断类型
     */
    private Map<String, String> inferSchemaFromMapRows(List<Map<String, Object>> rows, List<String> headers) {
        Map<String, String> schemaMap = new HashMap<>();

        for (String header : headers) {
            List<String> samples = new ArrayList<>();

            for (Map<String, Object> row : rows) {
                Object value = row.get(header);
                if (value != null) {
                    samples.add(String.valueOf(value));
                }
            }

            String inferredType = CsvUtils.inferType(samples);
            schemaMap.put(header, inferredType);
        }

        return schemaMap;
    }

    /**
     * 类型推断
     */
    private Map<String, String> inferSchema(List<Map<String, Object>> rows, List<String> headers) {
        Map<String, String> schemaMap = new HashMap<>();

        for (String header : headers) {
            List<String> samples = new ArrayList<>();

            for (Map<String, Object> row : rows) {
                Object value = row.get(header);
                if (value != null) {
                    samples.add(String.valueOf(value));
                }
            }

            String inferredType = CsvUtils.inferType(samples);
            schemaMap.put(header, inferredType);
        }

        return schemaMap;
    }

    /**
     * 统计分析
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> analyzeStats(
            List<Map<String, Object>> rows,
            List<String> originalHeaders,
            List<String> normHeaders) {

        Map<String, List<String>> columnSamples = new HashMap<>();
        Map<String, Set<String>> distinctValues = new HashMap<>();
        Map<String, Integer> nullCounts = new HashMap<>();

        for (Map<String, Object> row : rows) {
            for (int i = 0; i < originalHeaders.size(); i++) {
                String origHeader = originalHeaders.get(i);
                String normHeader = normHeaders.get(i);
                Object val = row.get(origHeader);

                if (val == null || StrUtil.isBlank(String.valueOf(val))) {
                    nullCounts.put(normHeader, nullCounts.getOrDefault(normHeader, 0) + 1);
                } else {
                    String valStr = String.valueOf(val);

                    distinctValues.computeIfAbsent(normHeader, k -> new HashSet<>()).add(valStr);

                    List<String> samples = columnSamples.computeIfAbsent(normHeader, k -> new ArrayList<>());
                    if (samples.size() < 100) {
                        samples.add(valStr);
                    }
                }
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("columnSamples", columnSamples);
        stats.put("distinctValues", distinctValues);
        stats.put("nullCounts", nullCounts);
        return stats;
    }

    /**
     * 写入 Parquet 文件
     */
    private Path writeParquetFile(
            FileContext fileContext,
            List<Map<String, Object>> rows,
            List<String> originalHeaders,
            List<String> normHeaders,
            Map<String, String> colTypes,
            String sheetName) throws IOException {

        Path tempParquet = Files.createTempFile("excel_" + sheetName + "_", ".parquet");

        // 构建列定义
        List<Map<String, String>> columnDefs = new ArrayList<>();
        for (int i = 0; i < normHeaders.size(); i++) {
            String normHeader = normHeaders.get(i);
            String origHeader = originalHeaders.get(i);

            Map<String, String> def = new HashMap<>();
            def.put("name", normHeader);
            def.put("type", colTypes.get(origHeader));
            columnDefs.add(def);
        }

        String uniqueTableName = "excel_" + fileContext.getDocumentId().replace("-", "_") + "_"
                + sheetName.replaceAll("[^a-zA-Z0-9]", "_");
        ParquetWriterUtil parquetWriter = new ParquetWriterUtil(
                duckdbDataSource, tempParquet, columnDefs, uniqueTableName);

        // 写入每一行
        for (Map<String, Object> row : rows) {
            Map<String, Object> record = new HashMap<>();

            for (int i = 0; i < originalHeaders.size(); i++) {
                String origHeader = originalHeaders.get(i);
                String normHeader = normHeaders.get(i);
                String type = colTypes.get(origHeader);

                Object rawVal = row.get(origHeader);
                String valStr = rawVal == null ? null : String.valueOf(rawVal);

                Object cleanVal = CsvUtils.cleanValue(valStr, type);
                record.put(normHeader, cleanVal);
            }

            parquetWriter.write(record);
        }

        parquetWriter.close();
        return tempParquet;
    }

    /**
     * 生成 Schema JSON
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> generateSchemaJson(
            FileContext fileContext,
            String s3Prefix,
            int rowCount,
            List<String> originalHeaders,
            List<String> normHeaders,
            Map<String, String> colTypes,
            Map<String, Object> statsMap,
            String sheetName) {

        Map<String, List<String>> columnSamples = (Map<String, List<String>>) statsMap.get("columnSamples");
        Map<String, Set<String>> distinctValues = (Map<String, Set<String>>) statsMap.get("distinctValues");
        Map<String, Integer> nullCounts = (Map<String, Integer>) statsMap.get("nullCounts");

        Map<String, Object> schemaJson = new LinkedHashMap<>();
        schemaJson.put("dataset_id", fileContext.getDocumentId());
        schemaJson.put("sheet_name", sheetName);
        schemaJson.put("table_name", ExcelUtils.normalizeSheetName(sheetName));
        schemaJson.put("object_path", s3Prefix + "data.parquet");
        schemaJson.put("row_count", rowCount);

        // 列信息
        List<Map<String, Object>> columnsInfo = new ArrayList<>();
        for (int i = 0; i < originalHeaders.size(); i++) {
            String origHeader = originalHeaders.get(i);
            String normHeader = normHeaders.get(i);
            String type = colTypes.get(origHeader);

            Map<String, Object> colInfo = new LinkedHashMap<>();
            colInfo.put("col_name", origHeader);
            colInfo.put("col_norm", normHeader);
            colInfo.put("duckdb_type", type);
            colInfo.put("description", "");

            // 统计
            Map<String, Object> stats = new LinkedHashMap<>();
            int nulls = nullCounts.getOrDefault(normHeader, 0);
            stats.put("null_ratio", rowCount == 0 ? 0 : (double) nulls / rowCount);
            stats.put("distinct_count", distinctValues.getOrDefault(normHeader, Collections.emptySet()).size());
            colInfo.put("stats", stats);

            // 样例
            List<String> samples = columnSamples.getOrDefault(normHeader, Collections.emptyList());
            colInfo.put("examples", samples.subList(0, Math.min(samples.size(), 5)));

            columnsInfo.add(colInfo);
        }

        schemaJson.put("columns", columnsInfo);
        return schemaJson;
    }

    /**
     * 生成数据集卡片
     */
    @SuppressWarnings("unchecked")
    private String generateDatasetCard(
            FileContext fileContext,
            int rowCount,
            Map<String, Object> schemaJson,
            List<Map<String, Object>> rows,
            String sheetName) {

        List<Map<String, Object>> columnsInfo = (List<Map<String, Object>>) schemaJson.get("columns");

        StringBuilder rawCardBuilder = new StringBuilder();
        rawCardBuilder.append("数据集名称: ").append(fileContext.getFileName())
                .append(" - Sheet: ").append(sheetName).append("\n");
        rawCardBuilder.append("记录数: ").append(rowCount).append("\n");
        rawCardBuilder.append("字段列表:\n");

        for (Map<String, Object> col : columnsInfo) {
            rawCardBuilder.append("- ").append(col.get("col_norm"))
                    .append(" (").append(col.get("duckdb_type")).append("): ")
                    .append(col.get("col_name"));

            Object examples = col.get("examples");
            if (examples instanceof List) {
                List<String> exList = (List<String>) examples;
                if (!exList.isEmpty()) {
                    rawCardBuilder.append(" | 示例: ")
                            .append(String.join(", ", exList.subList(0, Math.min(3, exList.size()))));
                }
            }
            rawCardBuilder.append("\n");
        }

        rawCardBuilder.append("\n样本数据:\n");
        for (int i = 0; i < Math.min(rows.size(), 3); i++) {
            Map<String, Object> row = rows.get(i);
            rawCardBuilder.append(JSONUtil.toJsonStr(row)).append("\n");
        }

        String rawCard = rawCardBuilder.toString();

        // LLM 增强
        return datasetCardEnhancer.enhanceSafely(rawCard, fileContext.getFileName() + "_" + sheetName);
    }

    /**
     * 上传 Artifacts 到 S3
     */
    private void uploadArtifacts(String bucket, String s3Prefix, Path tempParquet, Path tempSchema, Path tempCard) {
        s3ClientUtils.putObjectByTempFile(bucket, s3Prefix + "data.parquet", tempParquet,
                "application/octet-stream");
        s3ClientUtils.putObjectByTempFile(bucket, s3Prefix + "schema.json", tempSchema, "application/json");
        s3ClientUtils.putObjectByTempFile(bucket, s3Prefix + "dataset_card.txt", tempCard, "text/plain");

        logger.info("Excel Sheet Artifacts 已上传至 {}/{}", bucket, s3Prefix);
    }

    /**
     * 将 Dataset Card 存入向量库
     */
    private void storeDatasetCard(String cardContent, FileContext fileContext, String s3Prefix, String sheetName) {
        String fileId = FileIdGenerator.generateFileId(fileContext.getKey());
        fileContext.setFileId(fileId);

        DocumentMetadataMap metadataMap = new DocumentMetadataMap(
                fileContext.getFileName(),
                fileContext.getDocumentId(),
                fileContext.getFileId(),
                S3FileType.XLSX.getMimeType(),
                true,
                "0",
                fileContext.getKbId());

        metadataMap.setSource(fileContext.getKey());
        metadataMap.setPageRange("Sheet: " + sheetName);

        Map<String, Object> meta = metadataMap.toMap();
        meta.put("sheet_name", sheetName);
        meta.put("s3_schema_path", s3Prefix + "schema.json");
        meta.put("s3_parquet_path", s3Prefix + "data.parquet");
        meta.put("s3_card_path", s3Prefix + "dataset_card.txt");

        org.springframework.ai.document.Document cardDoc = new org.springframework.ai.document.Document(cardContent,
                meta);

        List<org.springframework.ai.document.Document> esDocs = EsVectorDocumentConverter.convert(
                Collections.singletonList(cardDoc), fileContext);

        vectorStore.add(esDocs);
    }

    private Path writeTempJsonFile(Map<String, Object> json) throws IOException {
        Path temp = Files.createTempFile("schema_", ".json");
        FileUtil.writeUtf8String(JSONUtil.toJsonPrettyStr(json), temp.toFile());
        return temp;
    }

    private Path writeTempTextFile(String text) throws IOException {
        Path temp = Files.createTempFile("card_", ".txt");
        FileUtil.writeUtf8String(text, temp.toFile());
        return temp;
    }

    private void deleteTempFiles(List<Path> paths) {
        for (Path path : paths) {
            try {
                if (path != null)
                    Files.deleteIfExists(path);
            } catch (Exception e) {
                logger.warn("删除临时文件失败: {}", path, e);
            }
        }
    }
}

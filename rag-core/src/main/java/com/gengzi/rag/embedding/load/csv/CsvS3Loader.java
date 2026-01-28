package com.gengzi.rag.embedding.load.csv;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvReader;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.gengzi.config.S3Properties;
import com.gengzi.context.DocumentMetadataMap;
import com.gengzi.context.FileContext;
import com.gengzi.dao.Document;
import com.gengzi.dao.repository.DocumentRepository;
import com.gengzi.enums.FileProcessStatusEnum;
import com.gengzi.enums.S3FileType;
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 加载S3上的CSV文件 (V2: 清洗、规范化、Parquet)
 *
 * @author: gengzi
 */
@Component
public class CsvS3Loader {

    private static final Logger logger = LoggerFactory.getLogger(CsvS3Loader.class);

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
    private CsvHeaderExtractor csvHeaderExtractor;

    public void csvParse(String filePath, Document document) {
        String documentId = document.getId();
        String kbId = document.getKbId();
        String defaultBucketName = s3Properties.getDefaultBucketName();
        // 获取文件元信息
        var headObjectResponse = s3ClientUtils.headObject(defaultBucketName, filePath);
        FileContext fileContext = FileContext.from(headObjectResponse, defaultBucketName, filePath, documentId, kbId);

        // 异步解析
        asyncParseCsv(fileContext);
    }

    @Transactional(rollbackFor = Exception.class)
    public CompletableFuture<?> asyncParseCsv(FileContext fileContext) {
        return CompletableFuture.runAsync(() -> {
            Path tempParquet = null;
            Path tempSchema = null;
            Path tempCard = null;
            try {
                // 1. 下载CSV文件内容
                byte[] csvBytes = s3ClientUtils.getObject(fileContext.getBucketName(), fileContext.getKey());
                if (csvBytes == null || csvBytes.length == 0) {
                    logger.error("CSV文件内容为空: {}", fileContext.getKey());
                    return;
                }

                // 2. 使用LLM智能提取表头
                List<String> originalHeaders;
                List<CsvRow> rows;

                try {
                    // 读取所有CSV行（不假设表头位置）
                    List<List<String>> allRawRows = readAllCsvRows(csvBytes);

                    if (CollUtil.isEmpty(allRawRows)) {
                        logger.error("CSV为空");
                        return;
                    }

                    // 使用LLM分析前10行
                    int rowsToAnalyze = Math.min(10, allRawRows.size());
                    List<List<String>> firstNRows = allRawRows.subList(0, rowsToAnalyze);

                    CsvHeaderExtractor.HeaderInfo headerInfo = csvHeaderExtractor.extractHeaderInfo(firstNRows);

                    if (headerInfo.isSuccess()) {
                        logger.info("LLM成功提取表头，位于第{}行", headerInfo.getHeaderRowIndex());

                        // 使用LLM提取的列名和数据行
                        originalHeaders = headerInfo.getColumnNames();

                        // 提取数据行（跳过表头及之前的行）
                        List<List<String>> dataRows = new ArrayList<>();
                        for (int i = headerInfo.getHeaderRowIndex() + 1; i < allRawRows.size(); i++) {
                            dataRows.add(allRawRows.get(i));
                        }

                        // 转换为CsvRow格式用于后续处理
                        rows = convertRawRowsToCsvRows(dataRows);
                    } else {
                        logger.warn("LLM表头提取失败: {}, 使用传统方法", headerInfo.getErrorMessage());
                        // 回退到原有逻辑
                        CsvData analysisData = readCsvData(csvBytes);
                        if (analysisData == null)
                            return;

                        rows = analysisData.getRows();
                        originalHeaders = analysisData.getHeader();

                        if (CollUtil.isEmpty(originalHeaders)) {
                            if (CollUtil.isNotEmpty(rows)) {
                                originalHeaders = rows.get(0).getRawList();
                                rows.remove(0);
                            } else {
                                logger.error("CSV为空或格式错误");
                                return;
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("LLM处理失败，使用传统方法", e);
                    // 完全回退到原有逻辑
                    CsvData analysisData = readCsvData(csvBytes);
                    if (analysisData == null)
                        return;

                    rows = analysisData.getRows();
                    originalHeaders = analysisData.getHeader();

                    if (CollUtil.isEmpty(originalHeaders)) {
                        if (CollUtil.isNotEmpty(rows)) {
                            originalHeaders = rows.get(0).getRawList();
                            rows.remove(0);
                        } else {
                            logger.error("CSV为空或格式错误");
                            return;
                        }
                    }
                }

                // 2. Pass 1: 分析 (规范化列名, 统计, 类型推断)
                List<String> normHeaders = normalizeHeaders(originalHeaders);
                Map<String, String> colTypes = new HashMap<>(); // 列名 -> 类型
                Map<String, Object> statsMap = analyzeStatsAndTypes(rows, normHeaders, colTypes);

                // 3. Pass 2: 清洗 & 写入 Parquet
                tempParquet = writeParquetFile(fileContext, rows, normHeaders, colTypes);

                // 4. 生成元数据文件 (Schema, Card)
                String s3Prefix = fileContext.getDocumentId() + "/";
                int rowCount = rows.size();

                Map<String, Object> schemaJson = generateSchemaJson(fileContext, s3Prefix, rowCount, originalHeaders,
                        normHeaders, colTypes, statsMap);
                tempSchema = writeTempJsonFile(schemaJson);

                String datasetCard = generateDatasetCard(fileContext, rowCount, schemaJson, rows);
                tempCard = writeTempTextFile(datasetCard);

                // 5. 上传到 S3
                uploadArtifacts(fileContext.getBucketName(), s3Prefix, tempParquet, tempSchema, tempCard);

                // 6. 存入向量库 (Dataset Card)
                storeDatasetCard(datasetCard, fileContext, s3Prefix);

            } catch (Exception e) {
                logger.error("CSV 处理异常", e);
                e.printStackTrace();
            } finally {
                // 移除临时文件
                deleteTempFiles(tempParquet, tempSchema, tempCard);
            }
        }, threadPoolTaskExecutor);
    }

    /**
     * 读取CSV数据
     */
    private CsvData readCsvData(byte[] csvBytes) {
        Charset charset = detectCharset(csvBytes);
        logger.info("CSV detected charset: {}", charset);
        CsvReader reader = CsvUtil.getReader();
        return reader.read(new InputStreamReader(new ByteArrayInputStream(csvBytes), charset));
    }

    private Charset detectCharset(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return StandardCharsets.UTF_8;
        }

        // UTF-8 BOM
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }

        List<Charset> candidates = Arrays.asList(
                StandardCharsets.UTF_8,
                Charset.forName("GB18030"),
                Charset.forName("GBK"),
                StandardCharsets.ISO_8859_1);

        for (Charset cs : candidates) {
            if (canDecode(bytes, cs)) {
                return cs;
            }
        }
        return StandardCharsets.UTF_8;
    }

    private boolean canDecode(byte[] bytes, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 规范化表头
     */
    private List<String> normalizeHeaders(List<String> originalHeaders) {
        Map<String, String> colNormMap = CsvUtils.normalizeColumns(originalHeaders);
        List<String> normHeaders = new ArrayList<>();
        for (String h : originalHeaders) {
            normHeaders.add(colNormMap.get(h));
        }
        return normHeaders;
    }

    /**
     * 分析统计信息和类型推断
     * 
     * @return 返回包含了统计信息（Samples, NullCounts, Distinct）的Map
     */
    private Map<String, Object> analyzeStatsAndTypes(List<CsvRow> rows, List<String> normHeaders,
            Map<String, String> colTypes) {
        Map<String, List<String>> columnSamples = new HashMap<>();
        Map<String, Set<String>> distinctValues = new HashMap<>();
        Map<String, Integer> nullCounts = new HashMap<>();

        for (CsvRow row : rows) {
            List<String> rawList = row.getRawList();
            for (int i = 0; i < rawList.size(); i++) {
                if (i >= normHeaders.size())
                    break;
                String colName = normHeaders.get(i);
                String val = rawList.get(i);

                // Null Check
                if (StrUtil.isBlank(val) || "null".equalsIgnoreCase(val) || "NA".equalsIgnoreCase(val)) {
                    nullCounts.put(colName, nullCounts.getOrDefault(colName, 0) + 1);
                } else {
                    // Distinct
                    distinctValues.computeIfAbsent(colName, k -> new HashSet<>()).add(val);

                    // Samples (Reservoir or simple first N)
                    List<String> samples = columnSamples.computeIfAbsent(colName, k -> new ArrayList<>());
                    if (samples.size() < 100) {
                        samples.add(val);
                    }
                }
            }
        }

        // 推断类型
        for (String col : normHeaders) {
            colTypes.put(col, CsvUtils.inferType(columnSamples.get(col)));
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
    private Path writeParquetFile(FileContext fileContext, List<CsvRow> rows, List<String> normHeaders,
            Map<String, String> colTypes)
            throws IOException {
        Path tempParquet = Files.createTempFile("data_", ".parquet");
        List<Map<String, String>> columnDefs = new ArrayList<>();
        for (String col : normHeaders) {
            Map<String, String> def = new HashMap<>();
            def.put("name", col);
            def.put("type", colTypes.get(col));
            columnDefs.add(def);
        }

        // 使用文档ID生成唯一的表名，避免多文件并发时的表名冲突
        String uniqueTableName = "csv_data_" + fileContext.getDocumentId().replace("-", "_");
        ParquetWriterUtil parquetWriter = new ParquetWriterUtil(duckdbDataSource, tempParquet, columnDefs,
                uniqueTableName);

        for (CsvRow row : rows) {
            Map<String, Object> record = new HashMap<>();
            List<String> rawList = row.getRawList();
            for (int i = 0; i < rawList.size(); i++) {
                if (i >= normHeaders.size())
                    break;
                String col = normHeaders.get(i);
                String type = colTypes.get(col);
                // 清洗值
                Object cleanVal = CsvUtils.cleanValue(rawList.get(i), type);
                record.put(col, cleanVal);
            }
            parquetWriter.write(record);
        }
        parquetWriter.close();
        return tempParquet;
    }

    /**
     * 生成 Schema JSON 对象
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> generateSchemaJson(FileContext fileContext, String s3Prefix, int rowCount,
            List<String> originalHeaders, List<String> normHeaders,
            Map<String, String> colTypes, Map<String, Object> statsMap) {

        Map<String, List<String>> columnSamples = (Map<String, List<String>>) statsMap.get("columnSamples");
        Map<String, Set<String>> distinctValues = (Map<String, Set<String>>) statsMap.get("distinctValues");
        Map<String, Integer> nullCounts = (Map<String, Integer>) statsMap.get("nullCounts");

        Map<String, Object> schemaJson = new LinkedHashMap<>();
        schemaJson.put("dataset_id", fileContext.getDocumentId());
        schemaJson.put("table_name", FileUtil.mainName(fileContext.getKey()).replaceAll("[^a-zA-Z0-9_]", "_"));
        schemaJson.put("object_path", s3Prefix + "data.parquet");
        schemaJson.put("row_count", rowCount);

        List<Map<String, Object>> columnsInfo = new ArrayList<>();
        for (int i = 0; i < originalHeaders.size(); i++) {
            String original = originalHeaders.get(i);
            String norm = normHeaders.get(i);
            String type = colTypes.get(norm);

            Map<String, Object> colInfo = new LinkedHashMap<>();
            colInfo.put("col_name", original);
            colInfo.put("col_norm", norm);
            colInfo.put("duckdb_type", type);
            colInfo.put("description", ""); // 暂时留空，后续可用 LLM 填充

            // Stats
            Map<String, Object> stats = new LinkedHashMap<>();
            int nulls = nullCounts.getOrDefault(norm, 0);
            stats.put("null_ratio", rowCount == 0 ? 0 : (double) nulls / rowCount);
            stats.put("distinct_count", distinctValues.getOrDefault(norm, Collections.emptySet()).size());
            colInfo.put("stats", stats);

            // Examples
            List<String> samples = columnSamples.getOrDefault(norm, Collections.emptyList());
            colInfo.put("examples", samples.subList(0, Math.min(samples.size(), 5)));

            columnsInfo.add(colInfo);
        }
        schemaJson.put("columns", columnsInfo);
        return schemaJson;
    }

    /**
     * 生成数据集卡片内容
     */
    @SuppressWarnings("unchecked")
    private String generateDatasetCard(FileContext fileContext, int rowCount, Map<String, Object> schemaJson,
            List<CsvRow> rows) {
        List<Map<String, Object>> columnsInfo = (List<Map<String, Object>>) schemaJson.get("columns");

        StringBuilder cardBuilder = new StringBuilder();
        cardBuilder.append("Dataset Name: ").append(fileContext.getFileName()).append("\n");
        cardBuilder.append("Row Count: ").append(rowCount).append("\n");
        cardBuilder.append("Columns:\n");
        for (Map<String, Object> col : columnsInfo) {
            cardBuilder.append("- ").append(col.get("col_norm"))
                    .append(" (").append(col.get("duckdb_type")).append("): ")
                    .append(col.get("col_name")).append("\n");
        }
        cardBuilder.append("\nSample Data:\n");
        // 添加少量样用于预览
        for (int i = 0; i < Math.min(rows.size(), 3); i++) {
            cardBuilder.append(rows.get(i).getRawList().stream().limit(5).collect(Collectors.joining(", ")))
                    .append("...\n");
        }
        return cardBuilder.toString();
    }

    /**
     * 上传所有 Artifacts 到 S3
     */
    private void uploadArtifacts(String bucket, String s3Prefix, Path tempParquet, Path tempSchema, Path tempCard) {
        // Upload Parquet
        s3ClientUtils.putObjectByTempFile(bucket, s3Prefix + "data.parquet", tempParquet, "application/octet-stream");
        // Upload Schema
        s3ClientUtils.putObjectByTempFile(bucket, s3Prefix + "schema.json", tempSchema, "application/json");
        // Upload Card
        s3ClientUtils.putObjectByTempFile(bucket, s3Prefix + "dataset_card.txt", tempCard, "text/plain");

        logger.info("CSV处理完成，Artifacts已上传至 {}/{}", bucket, s3Prefix);
    }

    /**
     * 将 Dataset Card 存入向量库
     */
    private void storeDatasetCard(String cardContent, FileContext fileContext, String s3Prefix) {
        String fileId = FileIdGenerator.generateFileId(fileContext.getKey()); // Init ID gen if needed
        fileContext.setFileId(fileId);
        DocumentMetadataMap metadataMap = new DocumentMetadataMap(
                fileContext.getFileName(),
                fileContext.getDocumentId(),
                fileContext.getFileId(),
                S3FileType.CSV.getMimeType(), // 使用 CSV MIME 类型，用于检索时识别为 CSV 数据集
                true,
                "0",
                fileContext.getKbId());

        // 设置来源路径
        metadataMap.setSource(fileContext.getKey());

        // 设置页码范围（CSV作为数据集，可以设置为 "dataset" 或总行数信息）
        metadataMap.setPageRange("dataset");

        // 增加用于 Router/SQL 的元数据
        Map<String, Object> meta = metadataMap.toMap();
        meta.put("s3_schema_path", s3Prefix + "schema.json");
        meta.put("s3_parquet_path", s3Prefix + "data.parquet");
        meta.put("s3_card_path", s3Prefix + "dataset_card.txt");

        org.springframework.ai.document.Document cardDoc = new org.springframework.ai.document.Document(cardContent,
                meta);

        List<org.springframework.ai.document.Document> esDocs = EsVectorDocumentConverter.convert(
                Collections.singletonList(cardDoc), fileContext);

        vectorStore.add(esDocs);

        // 更新处理状态
        documentRepository.updateChunkNumAndStatusById(fileContext.getDocumentId(), 1,
                String.valueOf(FileProcessStatusEnum.PROCESS_SUCCESS.getCode()));
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

    private void deleteTempFiles(Path... paths) {
        for (Path path : paths) {
            try {
                if (path != null)
                    Files.deleteIfExists(path);
            } catch (Exception e) {
                logger.warn("删除临时文件失败: {}", path, e);
            }
        }
    }

    /**
     * 读取所有CSV行（不假设表头）
     */
    private List<List<String>> readAllCsvRows(byte[] csvBytes) {
        try {
            Charset charset = detectCharset(csvBytes);
            CsvReader reader = CsvUtil.getReader();
            CsvData csvData = reader.read(new InputStreamReader(new ByteArrayInputStream(csvBytes), charset));

            List<List<String>> allRows = new ArrayList<>();

            // 如果有header，也当作第一行数据
            if (CollUtil.isNotEmpty(csvData.getHeader())) {
                allRows.add(csvData.getHeader());
            }

            // 添加所有数据行
            for (CsvRow row : csvData.getRows()) {
                allRows.add(row.getRawList());
            }

            return allRows;
        } catch (Exception e) {
            logger.error("读取CSV行失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 将List<List<String>>转换为List<CsvRow>
     * 使用CsvUtil重新解析CSV字符串来创建正确的CsvRow对象
     */
    private List<CsvRow> convertRawRowsToCsvRows(List<List<String>> rawRows) {
        if (CollUtil.isEmpty(rawRows)) {
            return new ArrayList<>();
        }

        try {
            // 将List<List<String>>转换回CSV字符串
            StringBuilder csvBuilder = new StringBuilder();
            for (List<String> row : rawRows) {
                // 使用引号包裹字段，避免包含逗号的字段被错误分割
                String rowStr = row.stream()
                        .map(cell -> "\"" + (cell == null ? "" : cell.replace("\"", "\"\"")) + "\"")
                        .collect(Collectors.joining(","));
                csvBuilder.append(rowStr).append("\n");
            }

            // 使用CsvReader重新解析
            String csvContent = csvBuilder.toString();
            CsvReader reader = CsvUtil.getReader();
            CsvData csvData = reader.readFromStr(csvContent);

            return csvData.getRows();
        } catch (Exception e) {
            logger.error("转换原始行到CsvRow失败", e);
            return new ArrayList<>();
        }
    }
}

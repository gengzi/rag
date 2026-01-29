package com.gengzi.rag.embedding.load.json;

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
import com.gengzi.rag.embedding.load.common.DatasetCardEnhancer;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 加载 S3 上的 JSON/JSONL 文件并进行 ETL 处理
 * 
 * @author: gengzi
 */
@Component
public class JsonS3Loader {

    private static final Logger logger = LoggerFactory.getLogger(JsonS3Loader.class);

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

    /**
     * JSON/JSONL 文件解析入口
     */
    public void jsonParse(String filePath, Document document) {
        String documentId = document.getId();
        String kbId = document.getKbId();
        String defaultBucketName = s3Properties.getDefaultBucketName();

        // 获取文件元信息
        var headObjectResponse = s3ClientUtils.headObject(defaultBucketName, filePath);
        FileContext fileContext = FileContext.from(headObjectResponse, defaultBucketName, filePath, documentId, kbId);

        // 异步解析
        asyncParseJson(fileContext);
    }

    @Transactional(rollbackFor = Exception.class)
    public CompletableFuture<?> asyncParseJson(FileContext fileContext) {
        return CompletableFuture.runAsync(() -> {
            Path tempParquet = null;
            Path tempSchema = null;
            Path tempCard = null;

            try {
                // 1. Extract: 下载并解析 JSON/JSONL
                byte[] jsonBytes = s3ClientUtils.getObject(fileContext.getBucketName(), fileContext.getKey());
                if (jsonBytes == null || jsonBytes.length == 0) {
                    logger.error("JSON 文件内容为空: {}", fileContext.getKey());
                    return;
                }

                List<Map<String, Object>> rows = JsonUtils.parseJson(jsonBytes);
                if (rows.isEmpty()) {
                    logger.error("JSON 解析结果为空");
                    return;
                }

                logger.info("成功解析 {} 条记录", rows.size());

                // 2. Transform: 提取所有键、规范化、类型推断
                List<String> originalKeys = JsonUtils.extractAllKeys(rows);

                // 规范化键名
                Map<String, String> keyNormMap = CsvUtils.normalizeColumns(originalKeys);
                List<String> normKeys = new ArrayList<>();
                for (String key : originalKeys) {
                    normKeys.add(keyNormMap.get(key));
                }

                // 类型推断
                Map<String, String> colTypes = JsonUtils.inferSchema(rows, originalKeys);

                // 统计信息
                Map<String, Object> statsMap = analyzeStats(rows, originalKeys, normKeys);

                // 3. Load: 写入 Parquet
                String s3Prefix = fileContext.getDocumentId() + "/";
                tempParquet = writeParquetFile(fileContext, rows, originalKeys, normKeys, colTypes);

                // 4. 生成元数据
                Map<String, Object> schemaJson = generateSchemaJson(
                        fileContext, s3Prefix, rows.size(), originalKeys, normKeys, colTypes, statsMap);
                tempSchema = writeTempJsonFile(schemaJson);

                String datasetCard = generateDatasetCard(fileContext, rows.size(), schemaJson, rows);
                tempCard = writeTempTextFile(datasetCard);

                // 5. 上传到 S3
                uploadArtifacts(fileContext.getBucketName(), s3Prefix, tempParquet, tempSchema, tempCard);

                // 6. 存入向量库
                storeDatasetCard(datasetCard, fileContext, s3Prefix);

                logger.info("JSON/JSONL 处理完成: {}", fileContext.getKey());

            } catch (Exception e) {
                logger.error("JSON/JSONL 处理异常", e);
                e.printStackTrace();
            } finally {
                deleteTempFiles(tempParquet, tempSchema, tempCard);
            }
        }, threadPoolTaskExecutor);
    }

    /**
     * 统计分析
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> analyzeStats(
            List<Map<String, Object>> rows,
            List<String> originalKeys,
            List<String> normKeys) {

        Map<String, List<String>> columnSamples = new HashMap<>();
        Map<String, Set<String>> distinctValues = new HashMap<>();
        Map<String, Integer> nullCounts = new HashMap<>();

        for (Map<String, Object> row : rows) {
            for (int i = 0; i < originalKeys.size(); i++) {
                String origKey = originalKeys.get(i);
                String normKey = normKeys.get(i);
                Object val = row.get(origKey);

                if (val == null || StrUtil.isBlank(String.valueOf(val))) {
                    nullCounts.put(normKey, nullCounts.getOrDefault(normKey, 0) + 1);
                } else {
                    String valStr = String.valueOf(val);

                    // Distinct
                    distinctValues.computeIfAbsent(normKey, k -> new HashSet<>()).add(valStr);

                    // Samples
                    List<String> samples = columnSamples.computeIfAbsent(normKey, k -> new ArrayList<>());
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
            List<String> originalKeys,
            List<String> normKeys,
            Map<String, String> colTypes) throws IOException {

        Path tempParquet = Files.createTempFile("json_data_", ".parquet");

        // 构建列定义
        List<Map<String, String>> columnDefs = new ArrayList<>();
        for (int i = 0; i < normKeys.size(); i++) {
            String normKey = normKeys.get(i);
            String origKey = originalKeys.get(i);

            Map<String, String> def = new HashMap<>();
            def.put("name", normKey);
            def.put("type", colTypes.get(origKey));
            columnDefs.add(def);
        }

        String uniqueTableName = "json_data_" + fileContext.getDocumentId().replace("-", "_");
        ParquetWriterUtil parquetWriter = new ParquetWriterUtil(
                duckdbDataSource, tempParquet, columnDefs, uniqueTableName);

        // 写入每一行
        for (Map<String, Object> row : rows) {
            Map<String, Object> record = new HashMap<>();

            for (int i = 0; i < originalKeys.size(); i++) {
                String origKey = originalKeys.get(i);
                String normKey = normKeys.get(i);
                String type = colTypes.get(origKey);

                Object rawVal = row.get(origKey);
                String valStr = rawVal == null ? null : String.valueOf(rawVal);

                // 清洗值
                Object cleanVal = CsvUtils.cleanValue(valStr, type);
                record.put(normKey, cleanVal);
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
            List<String> originalKeys,
            List<String> normKeys,
            Map<String, String> colTypes,
            Map<String, Object> statsMap) {

        Map<String, List<String>> columnSamples = (Map<String, List<String>>) statsMap.get("columnSamples");
        Map<String, Set<String>> distinctValues = (Map<String, Set<String>>) statsMap.get("distinctValues");
        Map<String, Integer> nullCounts = (Map<String, Integer>) statsMap.get("nullCounts");

        Map<String, Object> schemaJson = new LinkedHashMap<>();
        schemaJson.put("dataset_id", fileContext.getDocumentId());
        schemaJson.put("table_name", FileUtil.mainName(fileContext.getKey()).replaceAll("[^a-zA-Z0-9_]", "_"));
        schemaJson.put("object_path", s3Prefix + "data.parquet");
        schemaJson.put("row_count", rowCount);

        // 列信息
        List<Map<String, Object>> columnsInfo = new ArrayList<>();
        for (int i = 0; i < originalKeys.size(); i++) {
            String origKey = originalKeys.get(i);
            String normKey = normKeys.get(i);
            String type = colTypes.get(origKey);

            Map<String, Object> colInfo = new LinkedHashMap<>();
            colInfo.put("col_name", origKey);
            colInfo.put("col_norm", normKey);
            colInfo.put("duckdb_type", type);
            colInfo.put("description", "");

            // 统计
            Map<String, Object> stats = new LinkedHashMap<>();
            int nulls = nullCounts.getOrDefault(normKey, 0);
            stats.put("null_ratio", rowCount == 0 ? 0 : (double) nulls / rowCount);
            stats.put("distinct_count", distinctValues.getOrDefault(normKey, Collections.emptySet()).size());
            colInfo.put("stats", stats);

            // 样例
            List<String> samples = columnSamples.getOrDefault(normKey, Collections.emptyList());
            colInfo.put("examples", samples.subList(0, Math.min(samples.size(), 5)));

            columnsInfo.add(colInfo);
        }

        schemaJson.put("columns", columnsInfo);
        return schemaJson;
    }

    /**
     * 生成数据集卡片（LLM 增强版）
     */
    @SuppressWarnings("unchecked")
    private String generateDatasetCard(
            FileContext fileContext,
            int rowCount,
            Map<String, Object> schemaJson,
            List<Map<String, Object>> rows) {

        List<Map<String, Object>> columnsInfo = (List<Map<String, Object>>) schemaJson.get("columns");

        // 1. 先生成结构化信息（基础版）
        StringBuilder rawCardBuilder = new StringBuilder();
        rawCardBuilder.append("数据集名称: ").append(fileContext.getFileName()).append("\n");
        rawCardBuilder.append("记录数: ").append(rowCount).append("\n");
        rawCardBuilder.append("字段列表:\n");

        for (Map<String, Object> col : columnsInfo) {
            rawCardBuilder.append("- ").append(col.get("col_norm"))
                    .append(" (").append(col.get("duckdb_type")).append("): ")
                    .append(col.get("col_name"));

            // 添加样例值
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

        // 2. 使用 LLM 生成自然语言描述
        return datasetCardEnhancer.enhanceSafely(rawCard, fileContext.getFileName());
    }

    /**
     * 上传 Artifacts 到 S3
     */
    private void uploadArtifacts(String bucket, String s3Prefix, Path tempParquet, Path tempSchema, Path tempCard) {
        s3ClientUtils.putObjectByTempFile(bucket, s3Prefix + "data.parquet", tempParquet, "application/octet-stream");
        s3ClientUtils.putObjectByTempFile(bucket, s3Prefix + "schema.json", tempSchema, "application/json");
        s3ClientUtils.putObjectByTempFile(bucket, s3Prefix + "dataset_card.txt", tempCard, "text/plain");

        logger.info("JSON 处理完成，Artifacts 已上传至 {}/{}", bucket, s3Prefix);
    }

    /**
     * 将 Dataset Card 存入向量库
     */
    private void storeDatasetCard(String cardContent, FileContext fileContext, String s3Prefix) {
        String fileId = FileIdGenerator.generateFileId(fileContext.getKey());
        fileContext.setFileId(fileId);

        DocumentMetadataMap metadataMap = new DocumentMetadataMap(
                fileContext.getFileName(),
                fileContext.getDocumentId(),
                fileContext.getFileId(),
                S3FileType.JSON.getMimeType(),
                true,
                "0",
                fileContext.getKbId());

        metadataMap.setSource(fileContext.getKey());
        metadataMap.setPageRange("dataset");

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
        documentRepository.updateChunkNumAndStatusById(
                fileContext.getDocumentId(), 1,
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
}

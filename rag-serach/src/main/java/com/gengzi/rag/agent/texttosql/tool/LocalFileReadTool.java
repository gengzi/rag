package com.gengzi.rag.agent.texttosql.tool;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

/**
 * 本地文件读取工具 - 实现 Function 接口以支持 FunctionToolCallback
 */
@Component
public class LocalFileReadTool implements Function<LocalFileReadTool.ReadRequest, String> {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileReadTool.class);
    private static final String CACHE_ROOT_DIR = System.getProperty("java.io.tmpdir") + java.io.File.separator
            + "rag-texttosql-cache";

    /**
     * 读取请求类
     */
    public static class ReadRequest {
        private String documentId;

        public ReadRequest() {
        }

        public ReadRequest(String documentId) {
            this.documentId = documentId;
        }

        public String getDocumentId() {
            return documentId;
        }

        public void setDocumentId(String documentId) {
            this.documentId = documentId;
        }
    }

    /**
     * Function 接口实现 - 读取并解析schema.json文件
     */
    @Override
    public String apply(ReadRequest request) {
        String documentId = request.getDocumentId();
        try {
            logger.debug("读取schema文件: {}", documentId);

            Path schemaPath = Paths.get(CACHE_ROOT_DIR, documentId, "schema.json");

            if (!Files.exists(schemaPath)) {
                return String.format("错误：Schema文件不存在\n文档ID：%s\n提示：请先使用S3CacheTool下载文件", documentId);
            }

            // 读取并解析JSON
            String jsonContent = Files.readString(schemaPath, StandardCharsets.UTF_8);
            JSONObject schema = JSONUtil.parseObj(jsonContent);

            // 格式化输出
            StringBuilder result = new StringBuilder();
            result.append("数据集Schema信息：\n\n");
            result.append(String.format("数据集ID：%s\n", schema.getStr("dataset_id")));
            result.append(String.format("表名：%s\n", schema.getStr("table_name")));
            result.append(String.format("数据文件：%s\n", schema.getStr("object_path")));
            result.append(String.format("总行数：%s\n\n", schema.getStr("row_count")));

            result.append("列定义：\n");
            result.append("----------------------------------------\n");

            var columns = schema.getJSONArray("columns");
            if (columns != null) {
                for (int i = 0; i < columns.size(); i++) {
                    JSONObject col = columns.getJSONObject(i);
                    result.append(String.format("\n%d. %s (%s)\n",
                            i + 1,
                            col.getStr("col_norm"),
                            col.getStr("duckdb_type")));
                    result.append(String.format("   原始名称：%s\n", col.getStr("col_name")));

                    var stats = col.getJSONObject("stats");
                    if (stats != null) {
                        result.append(String.format("   空值率：%.2f%%\n",
                                stats.getDouble("null_ratio", 0.0) * 100));
                        result.append(String.format("   唯一值：%d\n",
                                stats.getInt("distinct_count", 0)));
                    }

                    var examples = col.getJSONArray("examples");
                    if (examples != null && !examples.isEmpty()) {
                        result.append("   示例值：");
                        for (int j = 0; j < Math.min(3, examples.size()); j++) {
                            if (j > 0)
                                result.append(", ");
                            result.append(examples.getStr(j));
                        }
                        result.append("\n");
                    }
                }
            }

            result.append("\n原始JSON：\n");
            result.append(JSONUtil.toJsonPrettyStr(schema));

            return result.toString();

        } catch (Exception e) {
            logger.error("读取schema文件失败: {}", documentId, e);
            return String.format("错误：读取schema失败 - %s\n详情：%s", documentId, e.getMessage());
        }
    }
}

package com.gengzi.rag.agent.texttosql.tool;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * DuckDB查询工具 - 实现 Function 接口以支持 FunctionToolCallback
 */
@Component
public class DuckDBQueryTool implements Function<DuckDBQueryTool.QueryRequest, String> {

    private static final Logger logger = LoggerFactory.getLogger(DuckDBQueryTool.class);
    private static final String CACHE_ROOT_DIR = System.getProperty("java.io.tmpdir") + java.io.File.separator
            + "rag-texttosql-cache";
    private static final int MAX_RESULT_ROWS = 1000;

    @Autowired
    @Qualifier("duckdbJdbcTemplate")
    private JdbcTemplate duckdbJdbcTemplate;

    /**
     * 查询请求类
     */
    public static class QueryRequest {
        private String documentId;
        private String sqlQuery;

        public QueryRequest() {
        }

        public QueryRequest(String documentId, String sqlQuery) {
            this.documentId = documentId;
            this.sqlQuery = sqlQuery;
        }

        public String getDocumentId() {
            return documentId;
        }

        public void setDocumentId(String documentId) {
            this.documentId = documentId;
        }

        public String getSqlQuery() {
            return sqlQuery;
        }

        public void setSqlQuery(String sqlQuery) {
            this.sqlQuery = sqlQuery;
        }
    }

    /**
     * Function 接口实现 - 查询Parquet数据
     */
    @Override
    public String apply(QueryRequest request) {
        String documentId = request.getDocumentId();
        String sqlQuery = request.getSqlQuery();
        String viewName = null;

        try {
            logger.info("执行DuckDB查询: documentId={}, sql={}", documentId, sqlQuery);

            // 1. 验证SQL语句
            String validationError = validateSqlQuery(sqlQuery);
            if (validationError != null) {
                return String.format("错误：SQL验证失败\n%s", validationError);
            }

            // 2. 确保Parquet文件已缓存
            Path parquetPath = Paths.get(CACHE_ROOT_DIR, documentId, "data.parquet");
            if (!Files.exists(parquetPath)) {
                return String.format(
                        "错误：数据文件不存在\n" +
                                "文档ID：%s\n" +
                                "预期路径：%s\n" +
                                "建议：请先使用 S3CacheTool下载文件",
                        documentId, parquetPath);
            }

            // 3. 生成唯一的视图名
            viewName = "data_" + documentId.replace("-", "_") + "_" + System.currentTimeMillis();

            // 4. 创建临时视图
            String createViewSql = String.format(
                    "CREATE OR REPLACE TEMP VIEW %s AS SELECT * FROM read_parquet('%s')",
                    viewName,
                    parquetPath.toAbsolutePath().toString().replace("\\", "/"));

            logger.debug("创建临时视图: {}", createViewSql);
            duckdbJdbcTemplate.execute(createViewSql);

            // 5. 替换SQL中的表名
            String actualSql = sqlQuery.replaceAll("(?i)\\bFROM\\s+data\\b", "FROM " + viewName);
            actualSql = actualSql.replaceAll("(?i)\\bJOIN\\s+data\\b", "JOIN " + viewName);

            // 6. 限制返回行数
            if (!actualSql.toUpperCase().contains("LIMIT")) {
                actualSql = actualSql + " LIMIT " + MAX_RESULT_ROWS;
            }

            logger.debug("执行SQL: {}", actualSql);

            // 7. 执行查询
            List<Map<String, Object>> results = duckdbJdbcTemplate.queryForList(actualSql);

            // 8. 格式化结果
            JSONObject response = new JSONObject();
            response.set("success", true);
            response.set("documentId", documentId);
            response.set("rowCount", results.size());
            response.set("sql", sqlQuery);

            if (!results.isEmpty()) {
                JSONArray columns = new JSONArray();
                results.get(0).keySet().forEach(columns::add);
                response.set("columns", columns);
            } else {
                response.set("columns", new JSONArray());
            }

            JSONArray dataArray = new JSONArray();
            for (Map<String, Object> row : results) {
                dataArray.add(row);
            }
            response.set("data", dataArray);

            String jsonResult = JSONUtil.toJsonPrettyStr(response);
            logger.info("查询成功，返回 {} 行结果", results.size());

            return jsonResult;

        } catch (Exception e) {
            logger.error("DuckDB查询失败: documentId={}, sql={}", documentId, sqlQuery, e);
            return String.format("错误：查询失败\nSQL：%s\n详情：%s", sqlQuery, e.getMessage());
        } finally {
            // 清理临时视图
            if (viewName != null) {
                try {
                    duckdbJdbcTemplate.execute("DROP VIEW IF EXISTS " + viewName);
                    logger.debug("已清理临时视图: {}", viewName);
                } catch (Exception e) {
                    logger.warn("清理临时视图失败: {}", viewName, e);
                }
            }
        }
    }

    /**
     * 验证SQL查询
     */
    private String validateSqlQuery(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "SQL语句不能为空";
        }

        String upperSql = sql.trim().toUpperCase();

        if (!upperSql.startsWith("SELECT")) {
            return "仅支持SELECT查询语句";
        }

        String[] dangerousKeywords = { "DROP", "DELETE", "INSERT", "UPDATE", "TRUNCATE", "ALTER", "CREATE TABLE" };
        for (String keyword : dangerousKeywords) {
            if (upperSql.contains(keyword)) {
                return "SQL包含不被允许的关键字: " + keyword;
            }
        }

        return null;
    }
}

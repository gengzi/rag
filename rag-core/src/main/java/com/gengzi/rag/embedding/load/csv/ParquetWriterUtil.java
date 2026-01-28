package com.gengzi.rag.embedding.load.csv;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

/**
 * DuckDB Parquet Writer
 */
public class ParquetWriterUtil {

    private Connection connection;
    private final List<Map<String, String>> columnDefs;
    private final String tableName; // 改为参数化，避免多文件并发时的表名冲突
    private final Path outputPath;

    public ParquetWriterUtil(DataSource dataSource, Path outputPath, List<Map<String, String>> columnDefs,
            String tableName)
            throws IOException {
        this.outputPath = outputPath;
        this.columnDefs = columnDefs;
        this.tableName = tableName;

        try {
            // Get connection from DataSource
            this.connection = dataSource.getConnection();

            // Create Table (先删除可能存在的同名表，避免冲突)
            createTable();

        } catch (Exception e) {
            throw new IOException("DuckDB exception", e);
        }
    }

    private void createTable() throws Exception {
        // 先删除可能存在的同名表（防止重复处理或并发冲突）
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + tableName);
        }

        StringBuilder sql = new StringBuilder("CREATE TABLE ").append(tableName).append(" (");
        for (int i = 0; i < columnDefs.size(); i++) {
            Map<String, String> col = columnDefs.get(i);
            String name = col.get("name");
            String type = col.get("type");

            // Map types to DuckDB types
            String duckType = "VARCHAR";
            if ("LONG".equals(type))
                duckType = "BIGINT";
            else if ("DOUBLE".equals(type))
                duckType = "DOUBLE";
            else if ("TIMESTAMP".equals(type))
                duckType = "TIMESTAMP"; // or BIGINT if millis

            sql.append(name).append(" ").append(duckType);
            if (i < columnDefs.size() - 1)
                sql.append(", ");
        }
        sql.append(")");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql.toString());
        }
    }

    public void write(Map<String, Object> rowData) throws IOException {
        // Construct Insert SQL (Ideally this should be batched, but for simplicity we
        // do single insert or small batch)
        // For performance, prepared statement should be cached, but simplified here.

        // Use a cached PreparedStatement logic in a real high-perf scenario.
        // Here we build the SQL dynamically or reuse a PS if we refactor.
        // Let's reuse a PreparedStatement for the lifetime.

        try {
            if (insertStmt == null) {
                StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName).append(" VALUES (");
                for (int i = 0; i < columnDefs.size(); i++) {
                    sql.append("?");
                    if (i < columnDefs.size() - 1)
                        sql.append(", ");
                }
                sql.append(")");
                insertStmt = connection.prepareStatement(sql.toString());
            }

            for (int i = 0; i < columnDefs.size(); i++) {
                String name = columnDefs.get(i).get("name");
                String type = columnDefs.get(i).get("type");
                Object val = rowData.get(name);

                if (val == null) {
                    insertStmt.setObject(i + 1, null);
                } else {
                    if ("TIMESTAMP".equals(type)) {
                        // Long -> Timestamp
                        if (val instanceof Long) {
                            insertStmt.setTimestamp(i + 1, new java.sql.Timestamp((Long) val));
                        } else {
                            insertStmt.setObject(i + 1, val);
                        }
                    } else {
                        insertStmt.setObject(i + 1, val);
                    }
                }
            }
            insertStmt.executeUpdate();

        } catch (Exception e) {
            throw new IOException("Error writing row to DuckDB", e);
        }
    }

    private PreparedStatement insertStmt;

    public void close() throws IOException {
        try {
            if (insertStmt != null)
                insertStmt.close();

            // Export to Parquet
            // COPY tableName TO 'outputPath' (FORMAT PARQUET, CODEC 'SNAPPY')
            // Windows path handling might require escaping, keep it simple
            String exportSql = String.format("COPY %s TO '%s' (FORMAT PARQUET, CODEC 'SNAPPY')",
                    tableName, outputPath.toAbsolutePath().toString().replace("\\", "/"));

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(exportSql);
            }

            if (connection != null)
                connection.close();

        } catch (Exception e) {
            throw new IOException("Failed to export Parquet or close DuckDB", e);
        }
    }
}

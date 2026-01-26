//package com.gengzi.service.impl;
//
//import com.gengzi.request.ExcelQueryRequest;
//import com.gengzi.service.DuckDbQueryService;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Service;
//import reactor.core.publisher.Mono;
//import reactor.core.scheduler.Schedulers;
//
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.List;
//import java.util.Map;
//
///**
// * DuckDB 文件查询服务实现
// * 使用 Spring JdbcTemplate 进行数据访问
// */
//@Service
//public class DuckDbQueryServiceImpl implements DuckDbQueryService {
//
//    private static final Logger logger = LoggerFactory.getLogger(DuckDbQueryServiceImpl.class);
//
//    @Autowired
//    @Qualifier("duckdbJdbcTemplate")
//    private JdbcTemplate duckdbJdbcTemplate;
//
//    @Override
//    public Mono<List<Map<String, Object>>> query(ExcelQueryRequest request) {
//        return Mono.fromCallable(() -> {
//            if (request == null) {
//                throw new IllegalArgumentException("请求参数不能为空");
//            }
//
//            String sql;
//            if (request.getSql() != null && !request.getSql().isBlank()) {
//                // 如果用户直接提供 SQL，直接使用
//                sql = request.getSql();
//                logger.info("执行用户提供的 SQL: {}", sql);
//            } else if (request.getFilePath() != null && !request.getFilePath().isBlank()) {
//                // 根据 filePath 构建 SQL
//                sql = buildSqlFromFileRequest(request);
//                logger.info("从文件路径构建 SQL: {}", sql);
//            } else {
//                throw new IllegalArgumentException("必须提供 sql 或 filePath 参数");
//            }
//
//            return executeQueryInternal(sql, request);
//        }).subscribeOn(Schedulers.boundedElastic());
//    }
//
//    @Override
//    public Mono<List<Map<String, Object>>> executeQuery(String sql) {
//        return Mono.fromCallable(() -> {
//            if (sql == null || sql.isBlank()) {
//                throw new IllegalArgumentException("SQL 语句不能为空");
//            }
//            logger.info("执行 SQL: {}", sql);
//            return executeQueryInternal(sql, null);
//        }).subscribeOn(Schedulers.boundedElastic());
//    }
//
//    /**
//     * 内部执行查询的方法
//     */
//    private List<Map<String, Object>> executeQueryInternal(String sql, ExcelQueryRequest request) {
//        try {
//            // 初始化 DuckDB 配置
//            initializeDuckDb(request);
//
//            // 执行查询
//            List<Map<String, Object>> result = duckdbJdbcTemplate.queryForList(sql);
//            logger.info("查询成功，返回 {} 行数据", result.size());
//            return result;
//
//        } catch (Exception e) {
//            logger.error("查询执行失败: {}", e.getMessage(), e);
//            throw new RuntimeException("查询执行失败: " + e.getMessage(), e);
//        }
//    }
//
//    /**
//     * 初始化 DuckDB 配置和扩展
//     */
//    private void initializeDuckDb(ExcelQueryRequest request) {
//        // 配置 DuckDB 参数
//        duckdbJdbcTemplate.execute("SET memory_limit='2GB'");
//        duckdbJdbcTemplate.execute("SET enable_external_access=true");
//        logger.debug("DuckDB 配置完成");
//
//        // 判断是否需要 Excel 扩展
//        boolean needExcel = false;
//        if (request != null) {
//            if (request.getSql() != null) {
//                needExcel = request.getSql().toLowerCase().contains("read_xlsx");
//            } else if (request.getFilePath() != null) {
//                needExcel = request.getFilePath().toLowerCase().matches(".*\\.xlsx?");
//            }
//        }
//
//        if (needExcel) {
//            installExcelExtension();
//        }
//
//        // CSV 文件不需要额外扩展，DuckDB 内置支持
//    }
//
//    /**
//     * 安装并加载 Excel 扩展
//     */
//    private void installExcelExtension() {
//        try {
//            logger.info("正在安装 Excel 扩展...");
//            duckdbJdbcTemplate.execute("INSTALL 'excel'");
//            logger.info("正在加载 Excel 扩展...");
//            duckdbJdbcTemplate.execute("LOAD 'excel'");
//            logger.info("Excel 扩展加载成功");
//        } catch (Exception e) {
//            logger.warn("Excel 扩展加载失败: {}", e.getMessage());
//            throw new RuntimeException("无法加载 Excel 扩展，请检查网络连接或 DuckDB 版本", e);
//        }
//    }
//
//    /**
//     * 根据请求参数构建 SQL
//     */
//    private String buildSqlFromFileRequest(ExcelQueryRequest request) {
//        Path filePath = Path.of(request.getFilePath()).toAbsolutePath().normalize();
//        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
//            throw new IllegalArgumentException("文件不存在或不可读: " + filePath);
//        }
//
//        String normalizedPath = filePath.toString().replace("\\", "/");
//
//        boolean isCsv = Boolean.TRUE.equals(request.getIsCsv());
//        String fileName = filePath.getFileName().toString().toLowerCase();
//
//        // 自动检测文件类型
//        if (fileName.endsWith(".csv") || fileName.endsWith(".txt")) {
//            isCsv = true;
//        } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
//            isCsv = false;
//        }
//
//        StringBuilder sql = new StringBuilder("SELECT * FROM ");
//
//        if (isCsv) {
//            // CSV 文件使用 read_csv_auto
//            sql.append("read_csv_auto('")
//               .append(escapeSingleQuotes(normalizedPath))
//               .append("', header=true, encoding='UTF-8')");
//        } else {
//            // Excel 文件使用 read_xlsx
//            sql.append("read_xlsx('")
//               .append(escapeSingleQuotes(normalizedPath))
//               .append("'");
//            if (request.getSheetName() != null && !request.getSheetName().isBlank()) {
//                sql.append(", sheet='").append(escapeSingleQuotes(request.getSheetName())).append("'");
//            }
//            sql.append(", header=true)");
//        }
//
//        return sql.toString();
//    }
//
//    /**
//     * 转义 SQL 中的单引号
//     */
//    private String escapeSingleQuotes(String input) {
//        return input.replace("'", "''");
//    }
//}

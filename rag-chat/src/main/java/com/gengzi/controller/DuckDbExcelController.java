//package com.gengzi.controller;
//
//import com.gengzi.request.ExcelQueryRequest;
//import com.gengzi.service.DuckDbQueryService;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.MediaType;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//import reactor.core.publisher.Mono;
//
//import java.util.List;
//import java.util.Map;
//
///**
// * DuckDB 文件查询 Controller
// * 提供 Excel 和 CSV 文件的 SQL 查询接口
// */
//@RestController
//@RequestMapping("/duckdb")
//public class DuckDbExcelController {
//
//    private static final Logger logger = LoggerFactory.getLogger(DuckDbExcelController.class);
//
//    @Autowired
//    private DuckDbQueryService duckDbQueryService;
//
//    /**
//     * 查询 Excel/CSV 文件
//     * 支持两种方式：
//     * 1. 直接传入 SQL 语句
//     * 2. 传入 filePath，自动构建 SQL
//     *
//     * @param request 查询请求
//     * @return 查询结果
//     */
//    @PostMapping(value = "/query", produces = MediaType.APPLICATION_JSON_VALUE)
//    public Mono<List<Map<String, Object>>> query(@RequestBody ExcelQueryRequest request) {
//        logger.info("收到查询请求: {}", request);
//        return duckDbQueryService.query(request)
//                .doOnSuccess(result -> logger.info("查询成功，返回 {} 行", result.size()))
//                .doOnError(error -> logger.error("查询失败: {}", error.getMessage()));
//    }
//
//    /**
//     * 执行自定义 SQL 查询
//     *
//     * @param request 包含 SQL 语句的请求
//     * @return 查询结果
//     */
//    @PostMapping(value = "/execute", produces = MediaType.APPLICATION_JSON_VALUE)
//    public Mono<List<Map<String, Object>>> execute(@RequestBody ExcelQueryRequest request) {
//        if (request == null || request.getSql() == null || request.getSql().isBlank()) {
//            logger.warn("收到无效的执行请求：缺少 SQL 语句");
//            return Mono.error(new IllegalArgumentException("必须提供 SQL 语句"));
//        }
//
//        logger.info("收到执行 SQL 请求");
//        return duckDbQueryService.executeQuery(request.getSql())
//                .doOnSuccess(result -> logger.info("SQL 执行成功，返回 {} 行", result.size()))
//                .doOnError(error -> logger.error("SQL 执行失败: {}", error.getMessage()));
//    }
//}

package com.gengzi.service;

import com.gengzi.request.ExcelQueryRequest;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * DuckDB 文件查询服务接口
 * 支持 Excel 和 CSV 文件的 SQL 查询
 */
public interface DuckDbQueryService {

    /**
     * 查询 Excel/CSV 文件
     * 支持直接传入 SQL 或通过 filePath 自动构建 SQL
     *
     * @param request 查询请求
     * @return 查询结果列表
     */
    Mono<List<Map<String, Object>>> query(ExcelQueryRequest request);

    /**
     * 执行自定义 SQL 查询
     *
     * @param sql SQL 语句
     * @return 查询结果列表
     */
    Mono<List<Map<String, Object>>> executeQuery(String sql);
}

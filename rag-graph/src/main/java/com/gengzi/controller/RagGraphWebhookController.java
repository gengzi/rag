package com.gengzi.controller;

import com.gengzi.config.RagGraphBuildProperties;
import com.gengzi.service.graph.RagGraphBuildService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 知识图谱构建 Webhook 控制器
 *
 * <p>提供外部触发知识图谱构建的接口，支持以下构建模式：</p>
 * <ul>
 *   <li>单文档构建：根据 docId 构建单个文档的知识图谱</li>
 *   <li>批量构建：遍历整个 ES 索引，批量构建所有文档的知识图谱</li>
 *   <li>全索引构建：构建指定索引的所有文档</li>
 * </ul>
 *
 * <p>主要用途：</p>
 * <ol>
 *   <li>外部系统集成（如 CI/CD 流水线）</li>
 *   <li>文档更新时触发增量构建</li>
 *   <li>定时任务触发全量构建</li>
 *   <li>手动触发图谱重建</li>
 * </ol>
 *
 * <p>构建流程：</p>
 * <pre>
 * ES 查询 → 文档解析 → Neo4j 写入 → 实体提取 → 社区发现 → 报告生成
 * </pre>
 *
 * @author RAG Graph Development Team
 * @version 1.0
 * @see RestController
 */
@Tag(name = "构建任务", description = "GraphRAG 构建触发接口")
@RestController
@RequestMapping("/webhook")
public class RagGraphWebhookController {

    /**
     * RAG 图谱构建服务
     * 负责执行实际的图谱构建逻辑
     */
    private final RagGraphBuildService ragGraphBuildService;

    /**
     * RAG 图谱构建配置属性
     * 包含默认的索引名称、批量大小等配置
     */
    private final RagGraphBuildProperties properties;

    /**
     * 构造函数，通过依赖注入初始化服务和配置
     *
     * @param ragGraphBuildService RAG 图谱构建服务
     * @param properties RAG 图谱构建配置属性
     */
    public RagGraphWebhookController(RagGraphBuildService ragGraphBuildService, RagGraphBuildProperties properties) {
        this.ragGraphBuildService = ragGraphBuildService;
        this.properties = properties;
    }

    /**
     * 触发知识图谱构建任务
     *
     * <p>此接口用于触发从 Elasticsearch 到 Neo4j 的知识图谱构建流程。
     * 支持两种构建模式：</p>
     *
     * <p><strong>1. 单文档模式（提供 docId 参数）</strong></p>
     * <ul>
     *   <li>根据 docId 从 ES 查询文档及其所有分块</li>
     *   <li>构建 Document-Chunk 层次结构</li>
     *   <li>对每个分块执行实体提取</li>
     *   <li>建立实体-分块关联关系</li>
     *   <li>触发社区发现和报告生成</li>
     * </ul>
     *
     * <p><strong>2. 批量模式（不提供 docId 参数）</strong></p>
     * <ul>
     *   <li>使用 search-after 分页查询整个索引</li>
     *   <li>逐个文档构建图结构</li>
     *   <li>使用 upsert 模式更新 Neo4j</li>
     * </ul>
     *
     * <p>参数优先级：</p>
     * <ul>
     *   <li>indexName：如果提供则使用，否则使用配置文件中的默认值</li>
     *   <li>batchSize：如果提供则使用，否则使用配置文件中的默认值</li>
     *   <li>docId：如果提供则使用单文档模式，否则使用批量模式</li>
     * </ul>
     *
     * <p>使用示例：</p>
     * <pre>
     * # 单文档构建
     * POST /webhook/build-graph?docId=doc123
     *
     * # 批量构建（使用默认索引和批量大小）
     * POST /webhook/build-graph
     *
     * # 批量构建（指定索引和批量大小）
     * POST /webhook/build-graph?indexName=knowledge_index&amp;batchSize=100
     * </pre>
     *
     * <p>注意事项：</p>
     * <ul>
     *   <li>此接口是异步的，会立即返回，构建在后台执行</li>
     *   <li>批量模式可能耗时较长，建议使用监控工具跟踪进度</li>
     *   <li>单文档模式适合文档更新后的增量构建</li>
     *   <li>社区发现会在每个文档构建完成后自动触发</li>
     * </ul>
     *
     * @param indexName 可选，ES 索引名称。如果不提供，使用配置文件中的默认值
     * @param batchSize 可选，批量查询大小。如果不提供，使用配置文件中的默认值
     * @param docId 可选，文档 ID。如果提供，则构建单个文档的图谱；否则批量构建整个索引
     * @return 构建任务启动成功的响应消息
     */
    @Operation(
        summary = "触发知识图谱构建",
        description = "支持按索引全量构建或按 docId 单文档构建。异步执行，立即返回。"
    )
    @PostMapping("/build-graph")
    public ResponseEntity<String> buildGraph(
            @RequestParam(value = "indexName", required = false) String indexName,
            @RequestParam(value = "batchSize", required = false) Integer batchSize,
            @RequestParam(value = "docId", required = false) String docId) {

        // 解析 indexName 参数：如果未提供或为空，使用配置文件中的默认值
        String resolvedIndex = (indexName == null || indexName.isBlank())
            ? properties.getIndexName()
            : indexName;

        // 解析 batchSize 参数：如果未提供或小于1，使用配置文件中的默认值
        int resolvedBatchSize = (batchSize == null || batchSize < 1)
            ? properties.getBatchSize()
            : batchSize;

        // 执行图谱构建任务
        // 如果提供了 docId，则构建单个文档的图谱；否则批量构建整个索引
        ragGraphBuildService.buildFromIndex(resolvedIndex, resolvedBatchSize, docId);

        // 返回成功响应
        return ResponseEntity.ok("Graph build started for index: " + resolvedIndex);
    }
}

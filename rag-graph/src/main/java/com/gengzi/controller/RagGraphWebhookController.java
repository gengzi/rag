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
 * Webhook 入口，用于触发 GraphRAG 构建。
 */
@Tag(name = "构建任务", description = "GraphRAG 构建触发接口")
@RestController
@RequestMapping("/webhook")
public class RagGraphWebhookController {

    private final RagGraphBuildService ragGraphBuildService;
    private final RagGraphBuildProperties properties;

    /**
     * 通过构造器注入构建服务与配置。
     */
    public RagGraphWebhookController(RagGraphBuildService ragGraphBuildService, RagGraphBuildProperties properties) {
        this.ragGraphBuildService = ragGraphBuildService;
        this.properties = properties;
    }

    /**
     * 触发从 ES 写入 Neo4j 的图构建流程。
     */
    @Operation(summary = "触发构建", description = "支持按索引全量或按 docId 构建")
    @PostMapping("/build-graph")
    public ResponseEntity<String> buildGraph(@RequestParam(value = "indexName", required = false) String indexName,
                                             @RequestParam(value = "batchSize", required = false) Integer batchSize,
                                             @RequestParam(value = "docId", required = false) String docId) {
        String resolvedIndex = (indexName == null || indexName.isBlank())
            ? properties.getIndexName()
            : indexName;
        int resolvedBatchSize = (batchSize == null || batchSize < 1)
            ? properties.getBatchSize()
            : batchSize;

        ragGraphBuildService.buildFromIndex(resolvedIndex, resolvedBatchSize, docId);
        return ResponseEntity.ok("Graph build started for index: " + resolvedIndex);
    }
}

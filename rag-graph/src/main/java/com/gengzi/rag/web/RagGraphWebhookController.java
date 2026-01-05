package com.gengzi.rag.web;

import com.gengzi.rag.graph.RagGraphBuildProperties;
import com.gengzi.rag.graph.RagGraphBuildService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook endpoints for triggering GraphRAG builds.
 */
@RestController
@RequestMapping("/webhook")
public class RagGraphWebhookController {

    private final RagGraphBuildService ragGraphBuildService;
    private final RagGraphBuildProperties properties;

    /**
     * Create a webhook controller with build service and config.
     */
    public RagGraphWebhookController(RagGraphBuildService ragGraphBuildService, RagGraphBuildProperties properties) {
        this.ragGraphBuildService = ragGraphBuildService;
        this.properties = properties;
    }

    /**
     * Trigger a full graph build from ES into Neo4j.
     */
    @PostMapping("/build-graph")
    public ResponseEntity<String> buildGraph(@RequestParam(value = "indexName", required = false) String indexName,
                                             @RequestParam(value = "batchSize", required = false) Integer batchSize) {
        String resolvedIndex = (indexName == null || indexName.isBlank())
                ? properties.getIndexName()
                : indexName;
        int resolvedBatchSize = (batchSize == null || batchSize < 1)
                ? properties.getBatchSize()
                : batchSize;

        ragGraphBuildService.buildFromIndex(resolvedIndex, resolvedBatchSize);
        return ResponseEntity.ok("Graph build started for index: " + resolvedIndex);
    }
}

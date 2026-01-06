package com.gengzi.controller;


import com.gengzi.model.neo4j.Document;
import com.gengzi.model.neo4j.KnowledgeEntity;
import com.gengzi.repository.neo4j.KnowledgeEntityRepository;
import com.gengzi.repository.neo4j.Neo4jDocumentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "图谱查询", description = "Neo4j 图谱写入与查询接口")
@RestController
@RequestMapping("/api/graph")
public class GraphKnowledgeController {

    private final Neo4jDocumentRepository neo4jDocumentRepository;
    private final KnowledgeEntityRepository entityRepository;

    // 构造器注入（Spring 推荐）
    public GraphKnowledgeController(Neo4jDocumentRepository neo4jDocumentRepository,
                                    KnowledgeEntityRepository entityRepository) {
        this.neo4jDocumentRepository = neo4jDocumentRepository;
        this.entityRepository = entityRepository;
    }

    // ==========================================
    // 1. 数据写入（Ingestion）
    // ==========================================

    /**
     * 保存完整的文档结构（包含 Chunk -> Mention -> Entity）。
     * SDN 的级联特性允许只保存根节点 Document，图结构会自动创建。
     */
    @Operation(summary = "保存文档图谱", description = "写入 Document 与关联的 Chunk/Mention/Entity")
    @PostMapping("/documents")
    public ResponseEntity<Document> saveDocument(@RequestBody Document document) {
        Document savedDoc = neo4jDocumentRepository.save(document);
        return ResponseEntity.ok(savedDoc);
    }

    // ==========================================
    // 2. 图谱查询（Graph Traversals）
    // ==========================================

    /**
     * 场景：知识溯源。
     * API: GET /api/graph/trace?entity=Neo4j
     * 作用：查询指定实体出现在哪些文档中。
     */
    @Operation(summary = "按实体溯源文档", description = "根据实体名称查询关联文档")
    @GetMapping("/trace")
    public ResponseEntity<List<Document>> traceDocumentsByEntity(@RequestParam String entity) {
        List<Document> documents = neo4jDocumentRepository.findDocumentsByEntityName(entity);
        return ResponseEntity.ok(documents);
    }

    /**
     * 场景：知识拓扑查询。
     * API: GET /api/graph/entities/{name}
     * 作用：查询实体的详细信息及其关联实体关系。
     */
    @Operation(summary = "实体关系查询", description = "查询实体及其关联关系")
    @GetMapping("/entities/{name}")
    public ResponseEntity<KnowledgeEntity> getEntityGraph(@PathVariable String name) {
        return entityRepository.findEntityWithRelationsByName(name)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 场景：文档详情。
     * API: GET /api/graph/documents/{docId}
     */
    @Operation(summary = "文档详情查询", description = "根据 docId 获取文档详情")
    @GetMapping("/documents/{docId}")
    public ResponseEntity<Document> getDocumentById(@PathVariable String docId) {
        Document document = neo4jDocumentRepository.findByDocId(docId).get(0);
        return ResponseEntity.ok(document);
    }
}

package com.gengzi.rag.web;


import com.gengzi.neo4j.node.Document;
import com.gengzi.neo4j.node.KnowledgeEntity;
import com.gengzi.neo4j.repository.KnowledgeEntityRepository;
import com.gengzi.neo4j.repository.Neo4jDocumentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/graph")
public class GraphKnowledgeController {

    private final Neo4jDocumentRepository neo4jDocumentRepository;
    private final KnowledgeEntityRepository entityRepository;

    // 构造器注入 (Spring 推荐方式)
    public GraphKnowledgeController(Neo4jDocumentRepository neo4jDocumentRepository,
                                    KnowledgeEntityRepository entityRepository) {
        this.neo4jDocumentRepository = neo4jDocumentRepository;
        this.entityRepository = entityRepository;
    }

    // ==========================================
    // 1. 数据写入 (Ingestion)
    // ==========================================

    /**
     * 保存完整的文档结构（包含 Chunk -> Mention -> Entity）
     * SDN 的级联特性(Cascade)允许我们只需保存根节点(Document)，
     * 整个图结构会自动创建。
     */
    @PostMapping("/documents")
    public ResponseEntity<Document> saveDocument(@RequestBody Document document) {
        Document savedDoc = neo4jDocumentRepository.save(document);
        return ResponseEntity.ok(savedDoc);
    }

    // ==========================================
    // 2. 图谱查询 (Graph Traversals)
    // ==========================================

    /**
     * 场景：知识溯源
     * API: GET /api/graph/trace?entity=Neo4j
     * 作用：查询 "Neo4j" 这个词出现在哪些文档中
     */
    @GetMapping("/trace")
    public ResponseEntity<List<Document>> traceDocumentsByEntity(@RequestParam String entity) {
        List<Document> documents = neo4jDocumentRepository.findDocumentsByEntityName(entity);
        return ResponseEntity.ok(documents);
    }

    /**
     * 场景：知识拓扑查看
     * API: GET /api/graph/entities/Neo4j
     * 作用：查看 "Neo4j" 的详细信息及其关联的其他实体（如 IS_A -> 图数据库）
     */
    @GetMapping("/entities/{name}")
    public ResponseEntity<KnowledgeEntity> getEntityGraph(@PathVariable String name) {
        return entityRepository.findEntityWithRelationsByName(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 场景：文档详情
     * API: GET /api/graph/documents/demo_neo4j_outline
     */
    @GetMapping("/documents/{docId}")
    public ResponseEntity<Document> getDocumentById(@PathVariable String docId) {
        Document document = neo4jDocumentRepository.findByDocId(docId).get(0);
        return ResponseEntity.ok(document);
    }
}
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

/**
 * Neo4j 知识图谱查询控制器
 *
 * <p>提供知识图谱的写入和查询接口，支持以下功能：</p>
 * <ul>
 *   <li>文档图谱写入：保存完整的文档-分块-实体层次结构</li>
 *   <li>知识溯源：查询实体出现在哪些文档中</li>
 *   <li>实体关系查询：查询实体的详细信息及其关联关系</li>
 *   <li>文档详情查询：根据文档 ID 获取完整信息</li>
 * </ul>
 *
 * <p>主要用途：</p>
 * <ol>
 *   <li>RAG 系统的知识图谱检索</li>
 *   <li>实体关系溯源和验证</li>
 *   <li>文档-实体关联查询</li>
 *   <li>知识图谱可视化数据源</li>
 * </ol>
 *
 * @author RAG Graph Development Team
 * @version 1.0
 * @see RestController
 */
@Tag(name = "图谱查询", description = "Neo4j 图谱写入与查询接口")
@RestController
@RequestMapping("/api/graph")
public class GraphKnowledgeController {

    /**
     * Neo4j 文档 Repository
     * 用于文档和分块的 CRUD 操作
     */
    private final Neo4jDocumentRepository neo4jDocumentRepository;

    /**
     * 知识实体 Repository
     * 用于实体的 CRUD 操作和关系查询
     */
    private final KnowledgeEntityRepository entityRepository;

    /**
     * 构造函数，通过依赖注入初始化 Repository
     * 使用构造器注入（Spring 推荐方式）
     *
     * @param neo4jDocumentRepository 文档 Repository
     * @param entityRepository 实体 Repository
     */
    public GraphKnowledgeController(Neo4jDocumentRepository neo4jDocumentRepository,
                                    KnowledgeEntityRepository entityRepository) {
        this.neo4jDocumentRepository = neo4jDocumentRepository;
        this.entityRepository = entityRepository;
    }

    // ==========================================
    // 1. 数据写入（Ingestion）
    // ==========================================

    /**
     * 保存完整的文档结构到 Neo4j
     *
     * <p>此接口用于保存文档及其关联的图结构，包括：</p>
     * <ul>
     *   <li>Document 节点</li>
     *   <li>Chunk 节点（通过 HAS_CHUNK 关系关联）</li>
     *   <li>Entity 节点（通过 MENTIONS 关系关联）</li>
     *   <li>Entity-Entity 关系（通过 RELATION_TYPE 关系）</li>
     * </ul>
     *
     * <p>由于 Spring Data Neo4j (SDN) 的级联保存特性，
     * 只需保存根节点 Document，所有关联的图结构会自动创建。</p>
     *
     * <p>数据模型：</p>
     * <pre>
     * (doc:Document)-[:HAS_CHUNK]->(chunk:Chunk)-[:MENTIONS]->(entity:Entity)
     * (entity:Entity)-[:RELATION_TYPE]->(entity:Entity)
     * </pre>
     *
     * <p>使用场景：</p>
     * <ul>
     *   <li>手动构建知识图谱时使用</li>
     *   <li>测试和调试图谱结构</li>
     *   <li>补充外部数据源</li>
     * </ul>
     *
     * @param document 包含完整图结构的文档对象
     * @return 保存后的文档对象（包含生成的 ID）
     */
    @Operation(
        summary = "保存文档图谱",
        description = "写入 Document 与关联的 Chunk/Mention/Entity，SDN 会自动创建所有关系"
    )
    @PostMapping("/documents")
    public ResponseEntity<Document> saveDocument(@RequestBody Document document) {
        // 保存文档及其所有关联节点（级联保存）
        Document savedDoc = neo4jDocumentRepository.save(document);
        return ResponseEntity.ok(savedDoc);
    }

    // ==========================================
    // 2. 图谱查询（Graph Traversals）
    // ==========================================

    /**
     * 根据实体名称溯源文档
     *
     * <p>此接口用于知识溯源，查询指定实体出现在哪些文档中。
     * 这对于验证实体来源、查看实体上下文非常有用。</p>
     *
     * <p>查询路径：</p>
     * <pre>
     * (entity:Entity)<-[:MENTIONS]-(chunk:Chunk)<-[:HAS_CHUNK]-(doc:Document)
     * </pre>
     *
     * <p>使用场景：</p>
     * <ul>
     *   <li>验证实体的文档来源</li>
     *   <li>查看实体在哪些文档中被提及</li>
     *   <li>实体溯源和可信度评估</li>
     *   <li>RAG 检索的来源验证</li>
     * </ul>
     *
     * <p>示例：</p>
     * <pre>
     * GET /api/graph/trace?entity=人工智能
     * </pre>
     *
     * @param entity 实体名称（如"人工智能"、"机器学习"等）
     * @return 包含该实体的所有文档列表
     */
    @Operation(
        summary = "按实体溯源文档",
        description = "根据实体名称查询关联文档，返回包含该实体的所有文档"
    )
    @GetMapping("/trace")
    public ResponseEntity<List<Document>> traceDocumentsByEntity(@RequestParam String entity) {
        // 通过实体名称查询包含该实体的所有文档
        List<Document> documents = neo4jDocumentRepository.findDocumentsByEntityName(entity);
        return ResponseEntity.ok(documents);
    }

    /**
     * 查询实体的详细信息及其关联关系
     *
     * <p>此接口用于查询实体的完整知识图谱，包括：</p>
     * <ul>
     *   <li>实体的基本信息（名称、类型、描述）</li>
     *   <li>实体的出度关系（该实体指向的其他实体）</li>
     *   <li>实体的入度关系（指向该实体的其他实体）</li>
     * </ul>
     *
     * <p>查询模式：</p>
     * <pre>
     * MATCH (e:Entity {name: $name})-[r:RELATION_TYPE]->(t:Entity)
     * RETURN e, r, t
     * </pre>
     *
     * <p>使用场景：</p>
     * <ul>
     *   <li>知识图谱可视化</li>
     *   <li>实体关系探索</li>
     *   <li>知识推理和验证</li>
     *   <li>实体详情展示</li>
     * </ul>
     *
     * <p>示例：</p>
     * <pre>
     * GET /api/graph/entities/机器学习
     * </pre>
     *
     * @param name 实体名称
     * @return 实体对象及其关联关系，如果未找到则返回 404
     */
    @Operation(
        summary = "实体关系查询",
        description = "查询实体及其关联的所有关系，用于知识图谱可视化和探索"
    )
    @GetMapping("/entities/{name}")
    public ResponseEntity<KnowledgeEntity> getEntityGraph(@PathVariable String name) {
        // 查询实体及其关联关系
        return entityRepository.findEntityWithRelationsByName(name)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据文档 ID 查询文档详情
     *
     * <p>此接口用于查询文档的完整信息，包括：</p>
     * <ul>
     *   <li>文档元数据（标题、创建时间等）</li>
     *   <li>文档的所有分块</li>
     *   <li>每个分块的内容和关联实体</li>
     *   <li>分块的顺序关系（NEXT 关系）</li>
     * </ul>
     *
     * <p>数据结构：</p>
     * <pre>
     * Document {
     *   docId: "doc123",
     *   title: "文档标题",
     *   chunks: [
     *     Chunk { content: "...", entities: [...] },
     *     Chunk { content: "...", entities: [...] }
     *   ]
     * }
     * </pre>
     *
     * <p>使用场景：</p>
     * <ul>
     *   <li>文档详情页面展示</li>
     *   <li>文档内容预览</li>
     *   <li>分块级别的实体查询</li>
     *   <li>文档结构分析</li>
     * </ul>
     *
     * <p>示例：</p>
     * <pre>
     * GET /api/graph/documents/doc123
     * </pre>
     *
     * @param docId 文档 ID
     * @return 文档对象及其完整结构
     */
    @Operation(
        summary = "文档详情查询",
        description = "根据 docId 获取文档详情，包括所有分块和关联实体"
    )
    @GetMapping("/documents/{docId}")
    public ResponseEntity<Document> getDocumentById(@PathVariable String docId) {
        // 根据 docId 查询文档（返回第一个匹配结果）
        Document document = neo4jDocumentRepository.findByDocId(docId).get(0);
        return ResponseEntity.ok(document);
    }
}

package com.gengzi.service.graph.impl;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.gengzi.model.es.KnowledgeDocument;
import com.gengzi.model.graph.RagGraphDocument;
import com.gengzi.model.neo4j.Chunk;
import com.gengzi.model.neo4j.Document;
import com.gengzi.repository.neo4j.Neo4jDocumentRepository;
import com.gengzi.service.es.EsRagSourceService;
import com.gengzi.service.es.KnowledgeDocumentService;
import com.gengzi.service.graph.GraphExtractionService;
import com.gengzi.service.graph.Neo4jGraphWriter;
import com.gengzi.service.graph.RagGraphBuildService;
import com.gengzi.util.GraphBuildUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * RAG 知识图谱构建服务实现类
 *
 * <p>负责从 Elasticsearch 中读取文档数据并构建 Neo4j 知识图谱。主要功能包括：</p>
 * <ul>
 *   <li>从 Elasticsearch 索引中批量读取文档</li>
 *   <li>构建文档-分块层次结构</li>
 *   <li>保存文档和分块到 Neo4j</li>
 *   <li>对每个分块执行实体提取</li>
 *   <li>建立实体与分块的关联关系</li>
 *   <li>触发社区发现和报告生成</li>
 * </ul>
 *
 * <p>构建流程：</p>
 * <pre>
 * ES 文档 → Document 节点 → Chunk 节点 → 实体提取 → Entity 节点 → 社区发现
 * </pre>
 *
 * @author RAG Graph Development Team
 * @version 1.0
 * @see RagGraphBuildService
 */
@Service
public class RagGraphBuildServiceImpl implements RagGraphBuildService {

    private static final Logger logger = LoggerFactory.getLogger(RagGraphBuildServiceImpl.class);

    /**
     * Elasticsearch RAG 数据源服务
     * 用于从 ES 索引中查询文档数据
     */
    private final EsRagSourceService esRagSourceService;

    /**
     * 知识文档服务
     * 用于通过 docId 查询文档及其分块
     */
    private final KnowledgeDocumentService knowledgeDocumentService;

    /**
     * Neo4j 文档 Repository
     * 用于保存文档和分块到图数据库
     */
    private final Neo4jDocumentRepository neo4jDocumentRepository;

    /**
     * Neo4j 图写入器
     * 用于批量构建和更新图结构
     */
    private final Neo4jGraphWriter neo4jGraphWriter;

    /**
     * 图谱提取服务
     * 用于从文本中提取实体和关系
     */
    private final GraphExtractionService graphExtractionService;

    /**
     * 构造函数，通过依赖注入初始化所有依赖项
     *
     * @param esRagSourceService Elasticsearch RAG 数据源服务
     * @param knowledgeDocumentService 知识文档服务
     * @param neo4jDocumentRepository Neo4j 文档 Repository
     * @param neo4jGraphWriter Neo4j 图写入器
     * @param graphExtractionService 图谱提取服务
     */
    public RagGraphBuildServiceImpl(EsRagSourceService esRagSourceService,
                                    KnowledgeDocumentService knowledgeDocumentService,
                                    Neo4jDocumentRepository neo4jDocumentRepository,
                                    Neo4jGraphWriter neo4jGraphWriter,
                                    GraphExtractionService graphExtractionService) {
        this.esRagSourceService = esRagSourceService;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.neo4jDocumentRepository = neo4jDocumentRepository;
        this.neo4jGraphWriter = neo4jGraphWriter;
        this.graphExtractionService = graphExtractionService;
    }

    /**
     * 从 Elasticsearch 索引构建知识图谱
     *
     * <p>支持两种构建模式：</p>
     * <ol>
     *   <li>单文档模式：根据 docId 构建单个文档的知识图谱</li>
     *   <li>批量模式：遍历整个索引，批量构建所有文档的知识图谱</li>
     * </ol>
     *
     * <p>单文档模式流程：</p>
     * <pre>
     * 1. 通过 docId 查询文档及其所有分块
     * 2. 构建 Document 和 Chunk 层次结构
     * 3. 保存到 Neo4j
     * 4. 对每个分块执行实体提取
     * 5. 建立实体-分块关联关系
     * 6. 触发社区发现和报告生成
     * </pre>
     *
     * <p>批量模式流程：</p>
     * <pre>
     * 1. 使用 search-after 分页查询所有文档
     * 2. 逐个文档构建图结构
     * 3. 使用 upsert 模式更新 Neo4j
     * </pre>
     *
     * @param indexName ES 索引名称
     * @param batchSize 批量查询大小（仅批量模式有效）
     * @param docId 文档 ID（如果提供则使用单文档模式）
     */
    @Override
    public void buildFromIndex(String indexName, int batchSize, String docId) {
        // 模式1: 单文档模式（根据 docId 构建）
        if (docId != null && !docId.isBlank()) {
            // 步骤1: 通过 docId 查询文档及其所有分块
            List<KnowledgeDocument> documents = knowledgeDocumentService.findByDocId(docId);
            if (documents.isEmpty()) {
                logger.info("No documents found for docId {}", docId);
                return;
            }

            // 步骤2: 构建 Document 和 Chunk 层次结构
            Document document = mapFromKnowledgeDocuments(documents, docId);
            if (document != null) {
                // 步骤3: 保存文档及其分块到 Neo4j
                Document savedDocument = neo4jDocumentRepository.save(document);
                logger.info("成功保存文档 {} 及其 {} 个分块到 Neo4j", docId, savedDocument.getChunks().size());

                // 步骤4: 对每个分块执行实体提取和关系构建
                // 这会触发社区发现和报告生成
                extractAndSaveEntitiesForChunks(savedDocument);
            }
            return;
        }

        // 模式2: 批量模式（遍历整个索引）
        List<FieldValue> searchAfter = null;

        // 使用 search-after 分页查询所有文档
        while (true) {
            SearchResponse<JsonData> response;
            try {
                // 查询一批文档
                response = esRagSourceService.search(indexName, batchSize, searchAfter);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to query Elasticsearch index: " + indexName, ex);
            }

            List<Hit<JsonData>> hits = response.hits().hits();
            // 如果没有更多文档，退出循环
            if (hits.isEmpty()) {
                logger.info("No more documents found in index {}", indexName);
                break;
            }

            // 处理每个文档：构建图结构并保存
            for (Hit<JsonData> hit : hits) {
                RagGraphDocument document = esRagSourceService.mapHit(hit);
                neo4jGraphWriter.upsertDocumentGraph(document);
            }

            // 获取最后一个文档的排序值，用于下一页查询
            Hit<JsonData> lastHit = hits.get(hits.size() - 1);
            searchAfter = lastHit.sort();
            if (searchAfter == null || searchAfter.isEmpty()) {
                logger.info("Search after values missing for index {}, stopping pagination", indexName);
                break;
            }
        }
    }

    /**
     * 将 KnowledgeDocument 列表映射为 Document 对象
     *
     * <p>此方法执行以下操作：</p>
     * <ol>
     *   <li>提取文档 ID（使用第一个非空字段）</li>
     *   <li>提取文档标题</li>
     *   <li>提取创建时间</li>
     *   <li>将每个 KnowledgeDocument 映射为 Chunk</li>
     *   <li>按分块编号排序（从 chunkId 中提取数字）</li>
     *   <li>建立分块间的 NEXT 关系（链表结构）</li>
     * </ol>
     *
     * <p>数据模型：</p>
     * <pre>
     * (doc:Document)-[:HAS_CHUNK]->(chunk1:Chunk)-[:NEXT]->(chunk2:Chunk)
     * </pre>
     *
     * @param documents 知识文档列表（同一文档的多个分块）
     * @param fallbackDocId 备用文档 ID（如果文档中没有 docId）
     * @return Document 对象，如果输入为空则返回 null
     */
    private Document mapFromKnowledgeDocuments(List<KnowledgeDocument> documents, String fallbackDocId) {
        if (documents == null || documents.isEmpty()) {
            return null;
        }

        // 使用第一个文档提取文档级别的元数据
        KnowledgeDocument first = documents.get(0);

        // 提取文档 ID（优先级：docId > id > fallbackDocId）
        String docId = GraphBuildUtil.firstNonBlank(first.getDocId(), first.getId(), fallbackDocId);
        if (docId == null || docId.isBlank()) {
            return null;
        }

        // 创建 Document 对象
        Document document = new Document();
        document.setDocId(docId);
        document.setTitle(GraphBuildUtil.firstNonBlank(first.getTitleTks(), first.getDocnmKwd(), first.getDocTypeKwd()));
        document.setCreatedAt(GraphBuildUtil.toLocalDate(first.getCreateTime()));

        // 映射所有分块
        List<Chunk> chunks = document.getChunks();
        for (int i = 0; i < documents.size(); i++) {
            Chunk chunk = mapChunk(documents.get(i), docId, i);
            if (chunk != null) {
                chunks.add(chunk);
            }
        }

        // 按分块编号排序（从 chunkId 中提取数字部分）
        List<Chunk> sortedChunks = chunks.stream().sorted(
                Comparator.comparingInt(this::extractChunkNumber)
        ).toList();

        // 建立分块间的 NEXT 关系（链表结构）
        // chunk1 -> chunk2 -> chunk3 -> ...
        for (int i = 0; i + 1 < sortedChunks.size(); i++) {
            chunks.get(i).setNextChunk(chunks.get(i + 1));
        }

        return document;
    }

    /**
     * 从 Chunk ID 中提取分块编号
     *
     * <p>假设 Chunk ID 格式为 "{docId}_{index}"，例如："doc123_0"、"doc123_1"。
     * 提取最后一个下划线后的数字作为分块编号。</p>
     *
     * @param chunk Chunk 对象
     * @return 分块编号，如果提取失败则返回 0
     */
    private int extractChunkNumber(Chunk chunk) {
        String id = chunk.getChunkId();
        // 加上 try-catch 防止 id 格式不对导致整个接口崩溃
        try {
            // 提取最后一个下划线后的数字
            return Integer.parseInt(id.substring(id.lastIndexOf("_") + 1));
        } catch (Exception e) {
            logger.warn("无法从分块 ID {} 中提取编号，使用默认值 0", id);
            return 0; // 或者抛出异常，视业务容错而定
        }
    }

    /**
     * 将 KnowledgeDocument 映射为 Chunk 对象
     *
     * <p>优先级顺序：</p>
     * <ol>
     *   <li>content：原始内容</li>
     *   <li>contentLtks：长文本分词</li>
     *   <li>contentSmLtks：短文本分词</li>
     * </ol>
     *
     * @param knowledgeDocument 知识文档对象
     * @param docId 文档 ID
     * @param index 分块索引
     * @return Chunk 对象，如果输入为 null 则返回 null
     */
    private Chunk mapChunk(KnowledgeDocument knowledgeDocument, String docId, int index) {
        if (knowledgeDocument == null) {
            return null;
        }

        Chunk chunk = new Chunk();
        chunk.setChunkId(knowledgeDocument.getId());
        // 使用第一个非空内容字段
        chunk.setContent(GraphBuildUtil.firstNonBlank(knowledgeDocument.getContent(),
                knowledgeDocument.getContentLtks(),
                knowledgeDocument.getContentSmLtks()));
        return chunk;
    }

    /**
     * 对文档的所有分块执行实体提取和关系构建
     *
     * <p>此方法遍历文档的每个分块，执行以下操作：</p>
     * <ol>
     *   <li>检查分块内容是否为空</li>
     *   <li>调用图提取服务提取实体和关系</li>
     *   <li>建立 Chunk 与 Entity 的 MENTIONS 关系</li>
     *   <li>触发社区发现和报告生成</li>
     *   <li>统计成功和失败数量</li>
     * </ol>
     *
     * <p>注意：</p>
     * <ul>
     *   <li>每个分块单独处理，失败的分块不影响其他分块</li>
     *   <li>实体提取完成后会自动触发社区发现</li>
     *   <li>社区发现完成后会自动生成社区报告</li>
     * </ul>
     *
     * @param document 已保存的文档对象（包含分块信息）
     */
    private void extractAndSaveEntitiesForChunks(Document document) {
        // 参数校验
        if (document == null || document.getChunks().isEmpty()) {
            logger.info("文档没有分块，跳过实体提取");
            return;
        }

        logger.info("开始为文档 {} 的 {} 个分块提取实体关系", document.getDocId(), document.getChunks().size());

        int successCount = 0;
        int failCount = 0;

        // 遍历所有分块，逐个提取实体
        for (Chunk chunk : document.getChunks()) {
            // 跳过空内容的分块
            if (chunk.getContent() == null || chunk.getContent().isBlank()) {
                logger.warn("分块 {} 内容为空，跳过实体提取", chunk.getChunkId());
                continue;
            }

            try {
                // 调用图提取服务提取实体和关系，并建立 Chunk-Entity 关系
                // 传递 chunkId 参数用于建立 MENTIONS 关系
                // 此方法会触发：
                // 1. 实体和关系保存到 Neo4j
                // 2. 社区发现算法执行
                // 3. 社区报告生成
                graphExtractionService.extractAndSaveEntities(chunk.getContent(), chunk.getChunkId());

                successCount++;
                logger.debug("成功为分块 {} 提取实体关系并建立关联", chunk.getChunkId());

            } catch (Exception e) {
                // 捕获异常，避免单个分块失败影响其他分块
                failCount++;
                logger.error("为分块 {} 提取实体关系失败: {}", chunk.getChunkId(), e.getMessage(), e);
            }
        }

        // 记录最终统计结果
        logger.info("文档 {} 实体提取完成，成功: {}，失败: {}",
                document.getDocId(), successCount, failCount);
    }
}

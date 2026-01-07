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

@Service
public class RagGraphBuildServiceImpl implements RagGraphBuildService {

    private static final Logger logger = LoggerFactory.getLogger(RagGraphBuildServiceImpl.class);

    private final EsRagSourceService esRagSourceService;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final Neo4jDocumentRepository neo4jDocumentRepository;
    private final Neo4jGraphWriter neo4jGraphWriter;
    private final GraphExtractionService graphExtractionService;

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

    @Override
    public void buildFromIndex(String indexName, int batchSize, String docId) {
        if (docId != null && !docId.isBlank()) {
            List<KnowledgeDocument> documents = knowledgeDocumentService.findByDocId(docId);
            if (documents.isEmpty()) {
                logger.info("No documents found for docId {}", docId);
                return;
            }

            // 1. 构建 Document 和 Chunk 关系
            Document document = mapFromKnowledgeDocuments(documents, docId);
            if (document != null) {
                Document savedDocument = neo4jDocumentRepository.save(document);
                logger.info("成功保存文档 {} 及其 {} 个分块到 Neo4j", docId, savedDocument.getChunks().size());

                // 2. 对每个 Chunk 执行实体提取和关系构建
                extractAndSaveEntitiesForChunks(savedDocument);
            }
            return;
        }

        List<FieldValue> searchAfter = null;

        while (true) {
            SearchResponse<JsonData> response;
            try {
                response = esRagSourceService.search(indexName, batchSize, searchAfter);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to query Elasticsearch index: " + indexName, ex);
            }

            List<Hit<JsonData>> hits = response.hits().hits();
            if (hits.isEmpty()) {
                logger.info("No more documents found in index {}", indexName);
                break;
            }

            for (Hit<JsonData> hit : hits) {
                RagGraphDocument document = esRagSourceService.mapHit(hit);
                neo4jGraphWriter.upsertDocumentGraph(document);
            }

            Hit<JsonData> lastHit = hits.get(hits.size() - 1);
            searchAfter = lastHit.sort();
            if (searchAfter == null || searchAfter.isEmpty()) {
                logger.info("Search after values missing for index {}, stopping pagination", indexName);
                break;
            }
        }
    }

    private Document mapFromKnowledgeDocuments(List<KnowledgeDocument> documents, String fallbackDocId) {
        if (documents == null || documents.isEmpty()) {
            return null;
        }
        KnowledgeDocument first = documents.get(0);
        String docId = GraphBuildUtil.firstNonBlank(first.getDocId(), first.getId(), fallbackDocId);
        if (docId == null || docId.isBlank()) {
            return null;
        }

        Document document = new Document();
        document.setDocId(docId);
        document.setTitle(GraphBuildUtil.firstNonBlank(first.getTitleTks(), first.getDocnmKwd(), first.getDocTypeKwd()));
        document.setCreatedAt(GraphBuildUtil.toLocalDate(first.getCreateTime()));

        List<Chunk> chunks = document.getChunks();
        for (int i = 0; i < documents.size(); i++) {
            Chunk chunk = mapChunk(documents.get(i), docId, i);
            if (chunk != null) {
                chunks.add(chunk);
            }
        }
        List<Chunk> sortedChunks = chunks.stream().sorted(
                Comparator.comparingInt(this::extractChunkNumber)
        ).toList();
        for (int i = 0; i + 1 < sortedChunks.size(); i++) {
            chunks.get(i).setNextChunk(chunks.get(i + 1));
        }

        return document;
    }

    private int extractChunkNumber(Chunk chunk) {
        String id = chunk.getChunkId();
        // 加上 try-catch 防止 id 格式不对导致整个接口崩溃
        try {
            return Integer.parseInt(id.substring(id.lastIndexOf("_") + 1));
        } catch (Exception e) {
            return 0; // 或者抛出异常，视业务容错而定
        }
    }

    private Chunk mapChunk(KnowledgeDocument knowledgeDocument, String docId, int index) {
        if (knowledgeDocument == null) {
            return null;
        }
        Chunk chunk = new Chunk();
        chunk.setChunkId(knowledgeDocument.getId());
        chunk.setContent(GraphBuildUtil.firstNonBlank(knowledgeDocument.getContent(),
                knowledgeDocument.getContentLtks(),
                knowledgeDocument.getContentSmLtks()));
        return chunk;
    }

    /**
     * 对文档的所有分块执行实体提取和关系构建
     * 将提取到的实体与分块建立关联关系
     *
     * @param document 已保存的文档对象（包含分块信息）
     */
    private void extractAndSaveEntitiesForChunks(Document document) {
        if (document == null || document.getChunks().isEmpty()) {
            logger.info("文档没有分块，跳过实体提取");
            return;
        }

        logger.info("开始为文档 {} 的 {} 个分块提取实体关系", document.getDocId(), document.getChunks().size());

        int successCount = 0;
        int failCount = 0;

        for (Chunk chunk : document.getChunks()) {
            if (chunk.getContent() == null || chunk.getContent().isBlank()) {
                logger.warn("分块 {} 内容为空，跳过实体提取", chunk.getChunkId());
                continue;
            }

            try {
                // 调用图提取服务提取实体和关系，并建立 Chunk-Entity 关系
                // 传递 chunkId 参数用于建立 MENTIONS 关系
                graphExtractionService.extractAndSaveEntities(chunk.getContent(), chunk.getChunkId());

                successCount++;
                logger.debug("成功为分块 {} 提取实体关系并建立关联", chunk.getChunkId());

            } catch (Exception e) {
                failCount++;
                logger.error("为分块 {} 提取实体关系失败: {}", chunk.getChunkId(), e.getMessage(), e);
            }
        }

        logger.info("文档 {} 实体提取完成，成功: {}，失败: {}",
                document.getDocId(), successCount, failCount);
    }
}

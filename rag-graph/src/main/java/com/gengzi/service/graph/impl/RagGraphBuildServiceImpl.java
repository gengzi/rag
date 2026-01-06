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
import com.gengzi.service.graph.Neo4jGraphWriter;
import com.gengzi.service.graph.RagGraphBuildService;
import com.gengzi.util.GraphBuildUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class RagGraphBuildServiceImpl implements RagGraphBuildService {

    private static final Logger logger = LoggerFactory.getLogger(RagGraphBuildServiceImpl.class);

    private final EsRagSourceService esRagSourceService;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final Neo4jDocumentRepository neo4jDocumentRepository;
    private final Neo4jGraphWriter neo4jGraphWriter;

    public RagGraphBuildServiceImpl(EsRagSourceService esRagSourceService,
                                    KnowledgeDocumentService knowledgeDocumentService,
                                    Neo4jDocumentRepository neo4jDocumentRepository,
                                    Neo4jGraphWriter neo4jGraphWriter) {
        this.esRagSourceService = esRagSourceService;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.neo4jDocumentRepository = neo4jDocumentRepository;
        this.neo4jGraphWriter = neo4jGraphWriter;
    }

    @Override
    public void buildFromIndex(String indexName, int batchSize, String docId) {
        if (docId != null && !docId.isBlank()) {
            List<KnowledgeDocument> documents = knowledgeDocumentService.findByDocId(docId);
            if (documents.isEmpty()) {
                logger.info("No documents found for docId {}", docId);
                return;
            }
            Document document = mapFromKnowledgeDocuments(documents, docId);
            if (document != null) {
                neo4jDocumentRepository.save(document);
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
        for (int i = 0; i + 1 < chunks.size(); i++) {
            chunks.get(i).setNextChunk(chunks.get(i + 1));
        }

        return document;
    }

    private Chunk mapChunk(KnowledgeDocument knowledgeDocument, String docId, int index) {
        if (knowledgeDocument == null) {
            return null;
        }
        Chunk chunk = new Chunk();
        chunk.setChunkId(docId + ":" + index);
        chunk.setContent(GraphBuildUtil.firstNonBlank(knowledgeDocument.getContent(),
            knowledgeDocument.getContentLtks(),
            knowledgeDocument.getContentSmLtks()));
        return chunk;
    }
}

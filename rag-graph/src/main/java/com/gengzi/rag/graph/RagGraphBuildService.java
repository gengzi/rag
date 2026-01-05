package com.gengzi.rag.graph;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.gengzi.rag.es.EsRagSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class RagGraphBuildService {

    private static final Logger logger = LoggerFactory.getLogger(RagGraphBuildService.class);

    private final EsRagSourceService esRagSourceService;
    private final Neo4jGraphWriter neo4jGraphWriter;

    public RagGraphBuildService(EsRagSourceService esRagSourceService, Neo4jGraphWriter neo4jGraphWriter) {
        this.esRagSourceService = esRagSourceService;
        this.neo4jGraphWriter = neo4jGraphWriter;
    }

    public void buildFromIndex(String indexName, int batchSize) {
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
}
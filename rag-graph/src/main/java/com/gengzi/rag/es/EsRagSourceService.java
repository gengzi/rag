package com.gengzi.rag.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.JsonpMapper;
import com.gengzi.rag.graph.RagGraphDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class EsRagSourceService {

    private final ElasticsearchClient client;
    private final EsRagDocumentMapper mapper;

    public EsRagSourceService(ElasticsearchClient client, EsRagDocumentMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public SearchResponse<JsonData> search(String indexName, int size, List<FieldValue> searchAfter) throws IOException {
        SearchRequest.Builder builder = new SearchRequest.Builder()
                .index(indexName)
                .size(size)
                .query(query -> query.matchAll(matchAll -> matchAll))
                .sort(sort -> sort.field(field -> field.field("_id").order(SortOrder.Asc)));

        if (searchAfter != null && !searchAfter.isEmpty()) {
            builder.searchAfter(searchAfter);
        }

        return client.search(builder.build(), JsonData.class);
    }

    public RagGraphDocument mapHit(Hit<JsonData> hit) {
        if (hit == null || hit.source() == null) {
            return null;
        }
        JsonpMapper jsonpMapper = client._transport().jsonpMapper();
        Map<String, Object> source = hit.source().to(Map.class, jsonpMapper);
        return mapper.map(source, hit.id());
    }
}

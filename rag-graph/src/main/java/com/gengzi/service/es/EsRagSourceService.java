package com.gengzi.service.es;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.gengzi.model.graph.RagGraphDocument;

import java.io.IOException;
import java.util.List;

/**
 * ES 数据拉取服务接口。
 */
public interface EsRagSourceService {

    /**
     * 按批次查询索引数据。
     */
    SearchResponse<JsonData> search(String indexName, int size, List<FieldValue> searchAfter) throws IOException;

    /**
     * 将 ES 文档映射为图构建模型。
     */
    RagGraphDocument mapHit(Hit<JsonData> hit);
}

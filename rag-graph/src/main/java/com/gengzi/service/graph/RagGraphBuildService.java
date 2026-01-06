package com.gengzi.service.graph;

/**
 * 图构建服务接口。
 */
public interface RagGraphBuildService {

    /**
     * 从指定 ES 索引构建图数据。
     */
    void buildFromIndex(String indexName, int batchSize, String docId);
}

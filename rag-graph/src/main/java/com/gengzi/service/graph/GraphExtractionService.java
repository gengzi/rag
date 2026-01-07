package com.gengzi.service.graph;

import com.gengzi.model.dto.EntityRelationDTO;

import java.util.List;

/**
 * 图谱提取服务接口
 * 用于从文本中提取实体和关系
 */
public interface GraphExtractionService {

    /**
     * 从文本中提取实体和关系
     * @param chunkText 文本内容
     * @return 提取结果，包含实体和关系的 JSON 格式字符串
     */
    String extractEntitiesAndRelations(String chunkText);

    /**
     * 从文本中提取实体和关系，并写入 Neo4j 图数据库
     * 同时建立 Chunk 与 Entity 的关系
     *
     * @param chunkText 文本内容
     * @param chunkId 分块ID（可选，用于建立 Chunk-Entity 关系）
     * @return 提取到的实体关系列表
     */
    List<EntityRelationDTO> extractAndSaveEntities(String chunkText, String... chunkId);
}

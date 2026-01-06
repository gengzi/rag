package com.gengzi.repository.neo4j;


import com.gengzi.model.neo4j.Document;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface Neo4jDocumentRepository extends Neo4jRepository<Document, String> {



    // 1. 基础查询：根据标题模糊查找
    List<Document> findByTitleContaining(String title);

    List<Document> findByDocId(String docId);

    /**
     * 2. 核心图查询：知识溯源
     * 场景：用户点击了知识图谱中的 "Neo4j" 节点，想看哪些文档提到了它。
     * 路径：Entity <- Mention <- Chunk <- Document
     */
    @Query("MATCH (d:Document)-[:HAS_CHUNK]->(c:Chunk)-[:HAS_MENTION]->(m:Mention)-[:REFERS_TO]->(e:Entity) " +
            "WHERE e.name = $entityName " +
            "RETURN DISTINCT d")
    List<Document> findDocumentsByEntityName(String entityName);

    /**
     * 3. 复杂条件：查找包含高置信度提及的文档
     */
    @Query("MATCH (d:Document)-[:HAS_CHUNK]->(c:Chunk)-[:HAS_MENTION]->(m:Mention) " +
            "WHERE m.confidence > $minConfidence " +
            "RETURN DISTINCT d")
    List<Document> findDocumentsWithHighConfidenceMentions(Double minConfidence);



}

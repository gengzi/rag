package com.gengzi.neo4j.repository;

import com.gengzi.neo4j.node.Chunk;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChunkRepository extends Neo4jRepository<Chunk, String> {

    /**
     * 1. 链表遍历：查找某个文档的所有切片，并按 index 排序
     * 虽然 Document 实体里有 list，但如果文档超大，单独分页查 Chunk 更高效。
     */
    @Query("MATCH (d:Document {doc_id: $docId})-[:HAS_CHUNK]->(c:Chunk) " +
           "RETURN c ORDER BY c.index ASC")
    List<Chunk> findAllByDocId(String docId);

    // 2. 关键词搜索：查找内容中包含特定文本的切片
    List<Chunk> findByContentContaining(String keyword);
}
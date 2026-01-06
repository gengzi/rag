package com.gengzi.repository.neo4j;


import com.gengzi.model.neo4j.KnowledgeEntity;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeEntityRepository extends Neo4jRepository<KnowledgeEntity, String> {

    // 1. 精确查找（配合 Optional 防止空指针）
    Optional<KnowledgeEntity> findByName(String name);

    /**
     * 2. 关系发现：查找某个实体的所有“下级”或“关联”实体
     * 场景：查询 "Neo4j" 是什么类型的？或者它归属于哪类？
     * 注意：这里我们返回完整的实体对象，SDN 会自动映射其中的 relations 属性
     */
    @Query("MATCH (start:Entity)-[r:RELATION_TYPE]->(end:Entity) " +
           "WHERE start.name = $name " +
           "RETURN start,collect(r), collect(end)")
    Optional<KnowledgeEntity> findEntityWithRelationsByName(String name);

    /**
     * 3. 推荐查询：协同过滤
     * 场景：查找同属于 "Concept" 类型的所有实体
     */
    List<KnowledgeEntity> findByType(String type);
}
package com.gengzi.model.neo4j;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

@RelationshipProperties
@Data
public class EntityRelation {

    // 关系本身的内部 ID，必须要有
    @RelationshipId
    private Long id;

    // 关系上的属性
    private String description;
    // 例如 IS_A, CATEGORY
    private String key;

    // 指向的目标节点
    @TargetNode
    private KnowledgeEntity targetEntity;
}
package com.gengzi.neo4j.node;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

@Node("Mention")
@Data
public class Mention {

    @Id
    @Property("mention_id")
    private String mentionId;

    private String span; // 原文中提及的文本片段

    private Double confidence; // 抽取置信度

    @Property("chunk_id") // 冗余字段，方便反查，可选
    private String chunkId;

    // 关系：提及指向具体的知识实体
    @Relationship(type = "REFERS_TO", direction = Relationship.Direction.OUTGOING)
    private KnowledgeEntity referredEntity;
}
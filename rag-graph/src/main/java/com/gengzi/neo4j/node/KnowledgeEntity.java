package com.gengzi.neo4j.node;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

@Node("Entity")
@Data
public class KnowledgeEntity {

    @Id
    private String id; // 对应数据中的 ent:technology:neo4j

    private String name;
    
    @Property("canonical_name")
    private String canonicalName;
    
    private String type; // Technology, Concept 等
    
    private String slug;

    private List<String> aliases = new ArrayList<>();

    // 关键点：使用上面定义的 EntityRelation 类来映射带属性的关系
    @Relationship(type = "RELATION_TYPE", direction = Relationship.Direction.OUTGOING)
    private List<EntityRelation> relations = new ArrayList<>();
}
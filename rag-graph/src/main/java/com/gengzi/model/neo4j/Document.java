package com.gengzi.model.neo4j;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Node("Document")
@Data
public class Document {

    // 使用输入数据中的 doc_id 作为主键
    @Id
    @Property("doc_id")
    private String docId;

    private String title;

    @Property("created_at")
    private LocalDate createdAt;

    // 关系：文档包含多个切片
    @Relationship(type = "HAS_CHUNK", direction = Relationship.Direction.OUTGOING)
    private List<Chunk> chunks = new ArrayList<>();
}
package com.gengzi.model.neo4j;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

@Node("Chunk")
@Data
public class Chunk {

    @Id
    @Property("chunk_id")
    private String chunkId;

    private String content;

    // 链表结构：指向下一个切片
    @Relationship(type = "NEXT_CHUNK", direction = Relationship.Direction.OUTGOING)
    private Chunk nextChunk;

    // 关系：切片包含多个提及
    @Relationship(type = "HAS_MENTION", direction = Relationship.Direction.OUTGOING)
    private List<Mention> mentions = new ArrayList<>();
}
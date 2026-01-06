package com.gengzi.service.graph;

import com.gengzi.model.graph.RagGraphDocument;
import com.gengzi.model.graph.RagGraphDocument.RagGraphChunk;
import com.gengzi.model.graph.RagGraphDocument.RagGraphEntity;
import com.gengzi.model.graph.RagGraphDocument.RagGraphEntityRelation;
import com.gengzi.model.graph.RagGraphDocument.RagGraphMention;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class Neo4jGraphWriter {

    private final Neo4jClient neo4jClient;

    public Neo4jGraphWriter(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    public void upsertDocumentGraph(RagGraphDocument document) {
        if (document == null || document.getDocId() == null || document.getDocId().isBlank()) {
            return;
        }

        mergeDocument(document);

        List<RagGraphChunk> chunks = new ArrayList<>(document.getChunks());
        chunks.sort(Comparator.comparingInt(chunk -> chunk.getIndex() == null ? Integer.MAX_VALUE : chunk.getIndex()));
        for (int i = 0; i < chunks.size(); i++) {
            RagGraphChunk chunk = chunks.get(i);
            if (chunk.getChunkId() == null || chunk.getChunkId().isBlank()) {
                continue;
            }

            mergeChunk(document.getDocId(), chunk);

            if (i + 1 < chunks.size()) {
                RagGraphChunk nextChunk = chunks.get(i + 1);
                if (nextChunk.getChunkId() != null && !nextChunk.getChunkId().isBlank()) {
                    mergeNextChunk(chunk.getChunkId(), nextChunk.getChunkId());
                }
            }

            for (RagGraphMention mention : chunk.getMentions()) {
                mergeMention(chunk.getChunkId(), mention);
                if (mention.getReferredEntity() != null) {
                    mergeEntity(mention.getReferredEntity());
                    mergeMentionEntityRelation(mention.getMentionId(), mention.getReferredEntity().getId());

                    for (RagGraphEntityRelation relation : mention.getReferredEntity().getRelations()) {
                        mergeEntityRelation(mention.getReferredEntity().getId(), relation);
                    }
                }
            }
        }
    }

    private void mergeDocument(RagGraphDocument document) {
        Map<String, Object> params = new HashMap<>();
        params.put("docId", document.getDocId());
        params.put("title", document.getTitle());
        params.put("url", document.getUrl());
        LocalDate createdAt = document.getCreatedAt();
        params.put("createdAt", createdAt == null ? null : createdAt.toString());

        neo4jClient.query("MERGE (d:Document {doc_id: $docId}) " +
                "SET d.title = $title, d.url = $url, d.created_at = $createdAt")
                .bindAll(params)
                .run();
    }

    private void mergeChunk(String docId, RagGraphChunk chunk) {
        Map<String, Object> params = new HashMap<>();
        params.put("docId", docId);
        params.put("chunkId", chunk.getChunkId());
        params.put("index", chunk.getIndex());
        params.put("content", chunk.getContent());

        neo4jClient.query("MERGE (d:Document {doc_id: $docId}) " +
                "MERGE (c:Chunk {chunk_id: $chunkId}) " +
                "SET c.index = $index, c.content = $content " +
                "MERGE (d)-[:HAS_CHUNK]->(c)")
                .bindAll(params)
                .run();
    }

    private void mergeNextChunk(String chunkId, String nextChunkId) {
        Map<String, Object> params = new HashMap<>();
        params.put("chunkId", chunkId);
        params.put("nextChunkId", nextChunkId);

        neo4jClient.query("MERGE (c:Chunk {chunk_id: $chunkId}) " +
                "MERGE (n:Chunk {chunk_id: $nextChunkId}) " +
                "MERGE (c)-[:NEXT_CHUNK]->(n)")
                .bindAll(params)
                .run();
    }

    private void mergeMention(String chunkId, RagGraphMention mention) {
        if (mention == null || mention.getMentionId() == null || mention.getMentionId().isBlank()) {
            return;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("chunkId", chunkId);
        params.put("mentionId", mention.getMentionId());
        params.put("span", mention.getSpan());
        params.put("confidence", mention.getConfidence());

        neo4jClient.query("MERGE (c:Chunk {chunk_id: $chunkId}) " +
                "MERGE (m:Mention {mention_id: $mentionId}) " +
                "SET m.span = $span, m.confidence = $confidence, m.chunk_id = $chunkId " +
                "MERGE (c)-[:HAS_MENTION]->(m)")
                .bindAll(params)
                .run();
    }

    private void mergeEntity(RagGraphEntity entity) {
        if (entity == null || entity.getId() == null || entity.getId().isBlank()) {
            return;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("entityId", entity.getId());
        params.put("name", entity.getName());
        params.put("canonicalName", entity.getCanonicalName());
        params.put("type", entity.getType());
        params.put("slug", entity.getSlug());
        params.put("aliases", entity.getAliases());

        neo4jClient.query("MERGE (e:Entity {id: $entityId}) " +
                "SET e.name = $name, e.canonical_name = $canonicalName, e.type = $type, e.slug = $slug, e.aliases = $aliases")
                .bindAll(params)
                .run();
    }

    private void mergeMentionEntityRelation(String mentionId, String entityId) {
        if (mentionId == null || mentionId.isBlank() || entityId == null || entityId.isBlank()) {
            return;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("mentionId", mentionId);
        params.put("entityId", entityId);

        neo4jClient.query("MERGE (m:Mention {mention_id: $mentionId}) " +
                "MERGE (e:Entity {id: $entityId}) " +
                "MERGE (m)-[:REFERS_TO]->(e)")
                .bindAll(params)
                .run();
    }

    private void mergeEntityRelation(String sourceEntityId, RagGraphEntityRelation relation) {
        if (sourceEntityId == null || sourceEntityId.isBlank() || relation == null) {
            return;
        }

        RagGraphEntity target = relation.getTargetEntity();
        if (target == null || target.getId() == null || target.getId().isBlank()) {
            return;
        }

        mergeEntity(target);

        Map<String, Object> params = new HashMap<>();
        params.put("sourceEntityId", sourceEntityId);
        params.put("targetEntityId", target.getId());
        params.put("key", relation.getKey());
        params.put("description", relation.getDescription());
        params.put("uncertain", relation.getUncertain());

        neo4jClient.query("MERGE (s:Entity {id: $sourceEntityId}) " +
                "MERGE (t:Entity {id: $targetEntityId}) " +
                "MERGE (s)-[r:RELATION_TYPE {key: $key}]->(t) " +
                "SET r.description = $description, r.uncertain = $uncertain")
                .bindAll(params)
                .run();
    }
}

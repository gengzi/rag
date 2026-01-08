package com.gengzi.repository.neo4j;

import com.gengzi.model.dto.ChunkHit;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Repository
public class ChunkRepositoryImpl implements ChunkRepositoryCustom {

    private final Neo4jClient neo4jClient;

    public ChunkRepositoryImpl(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @Override
    public List<ChunkHit> findLocalChunkHitsDepth1(List<String> entities, int limit) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        String query =
            "MATCH (e:Entity) " +
            "WHERE e.name IN $entities " +
            "WITH collect(DISTINCT e) AS seeds " +
            "UNWIND seeds AS seed " +
            "OPTIONAL MATCH (seed)-[:RELATION_TYPE*1..1]-(n:Entity) " +
            "WITH seeds, collect(DISTINCT n) AS neighbors " +
            "WITH seeds + neighbors AS nodes " +
            "UNWIND nodes AS ent " +
            "WITH ent WHERE ent IS NOT NULL " +
            "MATCH (ent)<-[:MENTIONS]-(ch:Chunk) " +
            "RETURN ch.chunk_id AS chunkId, ch.content AS content, collect(DISTINCT ent.name) AS entityNames " +
            "ORDER BY chunkId " +
            "LIMIT $limit";

        Collection<Map<String, Object>> rows = neo4jClient.query(query)
            .bindAll(Map.of("entities", entities, "limit", limit))
            .fetch()
            .all();

        return toChunkHits(rows, false);
    }

    @Override
    public List<ChunkHit> findLocalChunkHitsDepth2(List<String> entities, int limit) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        String query =
            "MATCH (e:Entity) " +
            "WHERE e.name IN $entities " +
            "WITH collect(DISTINCT e) AS seeds " +
            "UNWIND seeds AS seed " +
            "OPTIONAL MATCH (seed)-[:RELATION_TYPE*1..2]-(n:Entity) " +
            "WITH seeds, collect(DISTINCT n) AS neighbors " +
            "WITH seeds + neighbors AS nodes " +
            "UNWIND nodes AS ent " +
            "WITH ent WHERE ent IS NOT NULL " +
            "MATCH (ent)<-[:MENTIONS]-(ch:Chunk) " +
            "RETURN ch.chunk_id AS chunkId, ch.content AS content, collect(DISTINCT ent.name) AS entityNames " +
            "ORDER BY chunkId " +
            "LIMIT $limit";

        Collection<Map<String, Object>> rows = neo4jClient.query(query)
            .bindAll(Map.of("entities", entities, "limit", limit))
            .fetch()
            .all();

        return toChunkHits(rows, false);
    }

    @Override
    public List<ChunkHit> findGlobalChunkHits(List<String> keywords, int limit) {
        if (keywords == null || keywords.isEmpty()) {
            return Collections.emptyList();
        }

        String query =
            "MATCH (c:Community) " +
            "WHERE any(k IN $keywords WHERE " +
            "  any(ck IN coalesce(c.keywords, []) WHERE toLower(ck) CONTAINS toLower(k)) " +
            "  OR (c.title IS NOT NULL AND toLower(c.title) CONTAINS toLower(k)) " +
            "  OR (c.summary IS NOT NULL AND toLower(c.summary) CONTAINS toLower(k))" +
            ") " +
            "WITH DISTINCT c " +
            "MATCH (c)<-[:BELONGS_TO]-(e:Entity) " +
            "MATCH (e)<-[:MENTIONS]-(ch:Chunk) " +
            "RETURN ch.chunk_id AS chunkId, ch.content AS content, " +
            "collect(DISTINCT e.name) AS entityNames, collect(DISTINCT c.community_id) AS communityIds " +
            "ORDER BY chunkId " +
            "LIMIT $limit";

        Collection<Map<String, Object>> rows = neo4jClient.query(query)
            .bindAll(Map.of("keywords", keywords, "limit", limit))
            .fetch()
            .all();

        return toChunkHits(rows, true);
    }

    private List<ChunkHit> toChunkHits(Collection<Map<String, Object>> rows, boolean includeCommunities) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChunkHit> hits = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String chunkId = asString(row.get("chunkId"));
            String content = asString(row.get("content"));
            List<String> entityNames = castStringList(row.get("entityNames"));
            List<String> communityIds = includeCommunities
                ? castStringList(row.get("communityIds"))
                : Collections.emptyList();
            hits.add(new ChunkHit(chunkId, content, entityNames, communityIds));
        }
        return hits;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private List<String> castStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> results = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    results.add(item.toString());
                }
            }
            return results;
        }
        return Collections.emptyList();
    }
}

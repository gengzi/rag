package com.gengzi.service.graph.impl;

import com.gengzi.service.graph.CommunityGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CommunityGraphServiceImpl implements CommunityGraphService {

    private static final Logger logger = LoggerFactory.getLogger(CommunityGraphServiceImpl.class);
    private static final String GRAPH_NAME = "communityGraph";
    private static final int MAX_LEVELS = 2;

    private final Neo4jClient neo4jClient;

    public CommunityGraphServiceImpl(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @Override
    public void rebuildCommunities() {
        try {
            dropGraphIfExists();
            projectGraph();

            Collection<Map<String, Object>> rows = neo4jClient.query(
                    "CALL gds.louvain.stream($graphName, {maxLevels: $maxLevels}) " +
                            "YIELD nodeId, communityId, intermediateCommunityIds " +
                            "WITH gds.util.asNode(nodeId) AS n, communityId, intermediateCommunityIds " +
                            "WHERE n:Entity " +
                            "RETURN n.id AS entityId, " +
                            "CASE WHEN size(intermediateCommunityIds) > 0 THEN intermediateCommunityIds[0] ELSE communityId END AS level1Id, " +
                            "communityId AS level2Id")
                .bindAll(Map.of(
                    "graphName", GRAPH_NAME,
                    "maxLevels", MAX_LEVELS))
                .fetch()
                .all();

            if (rows.isEmpty()) {
                logger.info("No entity communities generated.");
                return;
            }

            persistCommunities(rows);
        } catch (Exception e) {
            logger.error("Failed to rebuild communities: {}", e.getMessage(), e);
        } finally {
            dropGraphIfExists();
        }
    }

    private void projectGraph() {
        neo4jClient.query(
                "CALL gds.graph.project($graphName, " +
                        "['Entity','Chunk'], " +
                        "{RELATION_TYPE: {type: 'RELATION_TYPE', orientation: 'UNDIRECTED'}, " +
                        " MENTIONS: {type: 'MENTIONS', orientation: 'UNDIRECTED'}})")
            .bindAll(Map.of("graphName", GRAPH_NAME))
            .run();
    }

    private void dropGraphIfExists() {
        neo4jClient.query("CALL gds.graph.drop($graphName, false)")
            .bindAll(Map.of("graphName", GRAPH_NAME))
            .run();
    }

    private void persistCommunities(Collection<Map<String, Object>> rows) {
        Map<String, Integer> level1Sizes = new HashMap<>();
        Map<String, Integer> level2Sizes = new HashMap<>();
        List<Map<String, Object>> belongsRows = new ArrayList<>();
        Set<String> parentEdges = new HashSet<>();

        for (Map<String, Object> row : rows) {
            String entityId = asString(row.get("entityId"));
            String level1Id = asString(row.get("level1Id"));
            String level2Id = asString(row.get("level2Id"));
            if (entityId == null || level2Id == null) {
                continue;
            }
            if (level1Id == null || level1Id.isBlank()) {
                level1Id = level2Id;
            }

            String level1Community = toLevel1Community(level1Id);
            String level2Community = toLevel2Community(level2Id);

            level1Sizes.put(level1Community, level1Sizes.getOrDefault(level1Community, 0) + 1);
            level2Sizes.put(level2Community, level2Sizes.getOrDefault(level2Community, 0) + 1);

            belongsRows.add(Map.of(
                "entityId", entityId,
                "level1Community", level1Community,
                "level2Community", level2Community
            ));
            parentEdges.add(level2Community + "->" + level1Community);
        }

        neo4jClient.query("MATCH (c:Community) DETACH DELETE c").run();

        List<Map<String, Object>> communityRows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : level1Sizes.entrySet()) {
            communityRows.add(Map.of("communityId", entry.getKey(), "level", 1, "size", entry.getValue()));
        }
        for (Map.Entry<String, Integer> entry : level2Sizes.entrySet()) {
            communityRows.add(Map.of("communityId", entry.getKey(), "level", 2, "size", entry.getValue()));
        }

        neo4jClient.query("UNWIND $rows AS row " +
                "MERGE (c:Community {community_id: row.communityId, level: row.level}) " +
                "SET c.size = row.size")
            .bindAll(Map.of("rows", communityRows))
            .run();

        neo4jClient.query("UNWIND $rows AS row " +
                "MATCH (e:Entity {id: row.entityId}) " +
                "MERGE (c1:Community {community_id: row.level1Community, level: 1}) " +
                "MERGE (c2:Community {community_id: row.level2Community, level: 2}) " +
                "MERGE (e)-[r1:BELONGS_TO]->(c1) " +
                "SET r1.level = 1 " +
                "MERGE (e)-[r2:BELONGS_TO]->(c2) " +
                "SET r2.level = 2")
            .bindAll(Map.of("rows", belongsRows))
            .run();

        List<Map<String, Object>> parentRows = new ArrayList<>();
        for (String edge : parentEdges) {
            String[] parts = edge.split("->", 2);
            parentRows.add(Map.of("parentId", parts[0], "childId", parts[1]));
        }

        neo4jClient.query("UNWIND $rows AS row " +
                "MERGE (parent:Community {community_id: row.parentId, level: 2}) " +
                "MERGE (child:Community {community_id: row.childId, level: 1}) " +
                "MERGE (parent)-[:PARENT_OF]->(child)")
            .bindAll(Map.of("rows", parentRows))
            .run();
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String toLevel1Community(String communityId) {
        return "L1:" + communityId;
    }

    private String toLevel2Community(String communityId) {
        return "L2:" + communityId;
    }
}

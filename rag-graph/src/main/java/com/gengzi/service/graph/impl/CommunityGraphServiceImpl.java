package com.gengzi.service.graph.impl;

import com.gengzi.service.graph.CommunityGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Neo4j 社区发现服务实现类
 *
 * <p>使用 Neo4j Graph Data Science (GDS) 库的 Louvain 算法进行社区发现，
 * 对知识图谱中的实体进行自动化的社区划分和层次化组织。</p>
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>图投影：将 Neo4j 原图投影到 GDS 内存图</li>
 *   <li>社区计算：使用 Louvain 算法进行社区划分</li>
 *   <li>结果持久化：将社区结构保存回 Neo4j 数据库</li>
 *   <li>层次化组织：支持2层社区结构（Level 1 和 Level 2）</li>
 * </ul>
 *
 * <p>数据模型：</p>
 * <pre>
 * Entity -[:BELONGS_TO]-> Community (level: 1 或 2)
 * Community (level: 2) -[:PARENT_OF]-> Community (level: 1)
 * </pre>
 *
 * @author RAG Graph Development Team
 * @version 1.0
 * @see CommunityGraphService
 */
@Service
public class CommunityGraphServiceImpl implements CommunityGraphService {

    private static final Logger logger = LoggerFactory.getLogger(CommunityGraphServiceImpl.class);

    /**
     * GDS 投影图的名称
     * 该图用于在内存中执行社区发现算法，提高计算性能
     */
    private static final String GRAPH_NAME = "communityGraph";

    /**
     * Louvain 算法的最大迭代层数
     *
     * <p>设置为 2 表示生成两层社区结构：</p>
 * <ul>
     *   <li>Level 1：粗粒度社区，由 intermediateCommunityIds[0] 表示</li>
     *   <li>Level 2：细粒度社区，由最终的 communityId 表示</li>
     * </ul>
     */
    private static final int MAX_LEVELS = 2;

    /**
     * Neo4j 客户端，用于执行 Cypher 查询
     */
    private final Neo4jClient neo4jClient;

    /**
     * 构造函数，通过依赖注入获取 Neo4j 客户端
     *
     * @param neo4jClient Neo4j 客户端实例
     */
    public CommunityGraphServiceImpl(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    /**
     * 重建社区结构
     *
     * <p>这是社区发现的主方法，执行以下步骤：</p>
     * <ol>
     *   <li>清理旧的 GDS 投影图（如果存在）</li>
     *   <li>将 Neo4j 原图投影到 GDS 内存图</li>
     *   <li>执行 Louvain 算法进行社区划分</li>
     *   <li>持久化社区结果到 Neo4j 数据库</li>
     *   <li>清理 GDS 投影图（释放内存）</li>
     * </ol>
     *
     * <p>Louvain 算法会生成两层社区结构：</p>
     * <ul>
     *   <li>Level 1 (粗粒度)：由 intermediateCommunityIds[0] 表示</li>
     *   <li>Level 2 (细粒度)：由最终的 communityId 表示</li>
     * </ul>
     *
     * <p>注意：此方法会删除所有现有的 Community 节点和关系，然后重新创建。</p>
     */
    @Override
    public void rebuildCommunities() {
        try {
            // 步骤1: 删除旧的 GDS 投影图（如果存在）
            dropGraphIfExists();

            // 步骤2: 投影图结构到 GDS 内存图
            projectGraph();

            // 步骤3: 执行 Louvain 算法并获取社区划分结果
            // 使用 stream 模式返回每个节点的社区归属信息
            Collection<Map<String, Object>> rows = neo4jClient.query(
                    "CALL gds.louvain.stream($graphName, {maxLevels: $maxLevels}) " +
                            "YIELD nodeId, communityId, intermediateCommunityIds " +
                            "WITH gds.util.asNode(nodeId) AS n, communityId, intermediateCommunityIds " +
                            "WHERE n:Entity " +  // 只处理实体节点
                            "RETURN n.id AS entityId, " +
                            "CASE WHEN size(intermediateCommunityIds) > 0 THEN intermediateCommunityIds[0] ELSE communityId END AS level1Id, " +
                            "communityId AS level2Id")  // level1Id 是粗粒度社区，level2Id 是细粒度社区
                .bindAll(Map.of(
                    "graphName", GRAPH_NAME,
                    "maxLevels", MAX_LEVELS))
                .fetch()
                .all();

            // 如果没有生成任何社区，记录日志并返回
            if (rows.isEmpty()) {
                logger.info("No entity communities generated.");
                return;
            }

            // 步骤4: 持久化社区结果到 Neo4j 数据库
            // 包括创建 Community 节点、BELONGS_TO 关系和 PARENT_OF 关系
            persistCommunities(rows);
        } catch (Exception e) {
            // 捕获并记录异常，避免影响主流程
            logger.error("Failed to rebuild communities: {}", e.getMessage(), e);
        } finally {
            // 步骤5: 清理 GDS 投影图，释放内存资源
            dropGraphIfExists();
        }
    }

    /**
     * 投影图结构到 GDS 内存图
     *
     * <p>将 Neo4j 数据库中的图结构投影到 GDS 内存图，以便快速执行图算法。</p>
     *
     * <p>投影配置：</p>
     * <ul>
     *   <li>节点类型：Entity（实体）和 Chunk（文档分块）</li>
     *   <li>关系类型：
     *     <ul>
     *       <li>RELATION_TYPE：实体间的语义关系，无向</li>
     *       <li>MENTIONS：实体在文档中被提及的关系，无向</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p>使用无向图是因为社区发现算法需要考虑节点间的双向关联。</p>
     */
    private void projectGraph() {
        neo4jClient.query(
                "CALL gds.graph.project($graphName, " +  // 图名称
                        "['Entity','Chunk'], " +          // 节点类型：实体和文档分块
                        "{RELATION_TYPE: {type: 'RELATION_TYPE', orientation: 'UNDIRECTED'}, " +  // 实体间关系，无向
                        " MENTIONS: {type: 'MENTIONS', orientation: 'UNDIRECTED'}})")  // 提及关系，无向
            .bindAll(Map.of("graphName", GRAPH_NAME))
            .run();
    }

    /**
     * 删除 GDS 投影图（如果存在）
     *
     * <p>在重新计算社区之前，需要先删除旧的投影图以释放内存。
     * 如果图不存在也不会报错（第二个参数为 false）。</p>
     *
     * <p>此方法在以下场景调用：</p>
     * <ul>
     *   <li>rebuildCommunities() 开始时：清理旧图</li>
     *   <li>rebuildCommunities() finally 块中：释放内存资源</li>
     * </ul>
     */
    private void dropGraphIfExists() {
        neo4jClient.query("CALL gds.graph.drop($graphName, false)")  // false 表示如果图不存在也不报错
            .bindAll(Map.of("graphName", GRAPH_NAME))
            .run();
    }

    /**
     * 持久化社区结果到 Neo4j 数据库
     *
     * <p>此方法执行以下操作：</p>
     * <ol>
     *   <li>解析 Louvain 算法返回的结果，统计社区大小</li>
     *   <li>删除所有现有的 Community 节点（全量重建）</li>
     *   <li>创建新的 Community 节点（Level 1 和 Level 2）</li>
     *   <li>建立 Entity 与 Community 的 BELONGS_TO 关系</li>
     *   <li>建立 Community 之间的 PARENT_OF 关系（层次结构）</li>
     * </ol>
     *
     * <p>数据模型：</p>
     * <pre>
     * Entity -[:BELONGS_TO]-> Community (level: 1)
     * Entity -[:BELONGS_TO]-> Community (level: 2)
     * Community (level: 2) -[:PARENT_OF]-> Community (level: 1)
     * </pre>
     *
     * @param rows Louvain 算法返回的结果集，包含每个实体的社区归属信息
     */
    private void persistCommunities(Collection<Map<String, Object>> rows) {
        // 用于统计每个社区的实体数量
        Map<String, Integer> level1Sizes = new HashMap<>();  // Level 1 社区大小统计
        Map<String, Integer> level2Sizes = new HashMap<>();  // Level 2 社区大小统计
        List<Map<String, Object>> belongsRows = new ArrayList<>();  // 实体-社区关系数据
        Set<String> parentEdges = new HashSet<>();  // 社区父子关系边（去重）

        // 遍历所有实体的社区归属结果
        for (Map<String, Object> row : rows) {
            String entityId = asString(row.get("entityId"));     // 实体ID
            String level1Id = asString(row.get("level1Id"));     // Level 1 社区ID（粗粒度）
            String level2Id = asString(row.get("level2Id"));     // Level 2 社区ID（细粒度）

            // 跳过无效数据
            if (entityId == null || level2Id == null) {
                continue;
            }

            // 如果没有 Level 1 社区（某些情况下可能不生成中间社区），则使用 Level 2 作为 Level 1
            if (level1Id == null || level1Id.isBlank()) {
                level1Id = level2Id;
            }

            // 为社区ID添加层级前缀，便于区分
            String level1Community = toLevel1Community(level1Id);  // 添加 "L1:" 前缀
            String level2Community = toLevel2Community(level2Id);  // 添加 "L2:" 前缀

            // 统计每个社区的实体数量
            level1Sizes.put(level1Community, level1Sizes.getOrDefault(level1Community, 0) + 1);
            level2Sizes.put(level2Community, level2Sizes.getOrDefault(level2Community, 0) + 1);

            // 准备实体-社区关系数据
            belongsRows.add(Map.of(
                "entityId", entityId,
                "level1Community", level1Community,
                "level2Community", level2Community
            ));

            // 记录社区父子关系（L2 -> L1），使用 Set 去重
            parentEdges.add(level2Community + "->" + level1Community);
        }

        // 步骤1: 删除所有现有的 Community 节点及其关系
        // 注意：这是全量重建，不是增量更新
        neo4jClient.query("MATCH (c:Community) DETACH DELETE c").run();

        // 步骤2: 准备 Community 节点数据
        List<Map<String, Object>> communityRows = new ArrayList<>();
        // 添加 Level 1 社区节点数据
        for (Map.Entry<String, Integer> entry : level1Sizes.entrySet()) {
            communityRows.add(Map.of("communityId", entry.getKey(), "level", 1, "size", entry.getValue()));
        }
        // 添加 Level 2 社区节点数据
        for (Map.Entry<String, Integer> entry : level2Sizes.entrySet()) {
            communityRows.add(Map.of("communityId", entry.getKey(), "level", 2, "size", entry.getValue()));
        }

        // 步骤3: 批量创建 Community 节点
        // 使用 MERGE 确保唯一性，即使重复执行也不会创建重复节点
        neo4jClient.query("UNWIND $rows AS row " +
                "MERGE (c:Community {community_id: row.communityId, level: row.level}) " +
                "SET c.size = row.size")  // 设置社区大小（实体数量）
            .bindAll(Map.of("rows", communityRows))
            .run();

        // 步骤4: 批量建立 Entity 与 Community 的 BELONGS_TO 关系
        // 每个实体同时属于一个 Level 1 社区和一个 Level 2 社区
        neo4jClient.query("UNWIND $rows AS row " +
                "MATCH (e:Entity {id: row.entityId}) " +
                "MERGE (c1:Community {community_id: row.level1Community, level: 1}) " +
                "MERGE (c2:Community {community_id: row.level2Community, level: 2}) " +
                "MERGE (e)-[r1:BELONGS_TO]->(c1) " +
                "SET r1.level = 1 " +  // 标记关系层级
                "MERGE (e)-[r2:BELONGS_TO]->(c2) " +
                "SET r2.level = 2")   // 标记关系层级
            .bindAll(Map.of("rows", belongsRows))
            .run();

        // 步骤5: 批量建立 Community 之间的 PARENT_OF 关系（层次结构）
        // Level 2 社区是 Level 1 社区的父节点
        List<Map<String, Object>> parentRows = new ArrayList<>();
        for (String edge : parentEdges) {
            String[] parts = edge.split("->", 2);
            parentRows.add(Map.of("parentId", parts[0], "childId", parts[1]));
        }

        neo4jClient.query("UNWIND $rows AS row " +
                "MERGE (parent:Community {community_id: row.parentId, level: 2}) " +
                "MERGE (child:Community {community_id: row.childId, level: 1}) " +
                "MERGE (parent)-[:PARENT_OF]->(child)")  // 建立父子关系
            .bindAll(Map.of("rows", parentRows))
            .run();
    }

    /**
     * 将对象转换为字符串
     *
     * @param value 要转换的对象
     * @return 字符串形式，如果对象为 null 则返回 null
     */
    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 为 Level 1 社区ID添加前缀
     *
     * <p>添加 "L1:" 前缀，便于区分不同层级的社区。
     * 例如：123 -> "L1:123"</p>
     *
     * @param communityId 原始社区ID
     * @return 带有前缀的 Level 1 社区ID
     */
    private String toLevel1Community(String communityId) {
        return "L1:" + communityId;
    }

    /**
     * 为 Level 2 社区ID添加前缀
     *
     * <p>添加 "L2:" 前缀，便于区分不同层级的社区。
     * 例如：456 -> "L2:456"</p>
     *
     * @param communityId 原始社区ID
     * @return 带有前缀的 Level 2 社区ID
     */
    private String toLevel2Community(String communityId) {
        return "L2:" + communityId;
    }
}

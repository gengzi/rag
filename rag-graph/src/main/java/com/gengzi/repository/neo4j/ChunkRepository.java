package com.gengzi.repository.neo4j;

import com.gengzi.model.neo4j.Chunk;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文档分块 Neo4j Repository
 *
 * <p>提供文档分块（Chunk）的数据库访问操作，包括�?/p>
 * <ul>
 *   <li>基础 CRUD 操作（继承自 Neo4jRepository�?/li>
 *   <li>链表遍历：按文档 ID 查询所有分�?/li>
 *   <li>关键词搜索：在分块内容中搜索关键�?/li>
 *   <li>局部检索：基于实体关系查找相关分块</li>
 *   <li>全局检索：基于社区关键词查找相关分�?/li>
 * </ul>
 *
 * <p>核心检索方法：</p>
 * <ul>
 *   <li>findLocalChunkHitsDepth1�?跳邻居检�?/li>
 *   <li>findLocalChunkHitsDepth2�?跳邻居检�?/li>
 *   <li>findGlobalChunkHits：社区关键词检�?/li>
 * </ul>
 *
 * @author RAG Graph Development Team
 * @version 1.0
 */
@Repository
public interface ChunkRepository extends Neo4jRepository<Chunk, String>, ChunkRepositoryCustom {

    /**
     * 查找某个文档的所有分块，并按索引排序
     *
     * <p>虽然 Document 实体里有 chunks 列表，但如果文档超大�?     * 单独分页查询 Chunk 更高效�?/p>
     *
     * <p>查询模式�?/p>
     * <pre>
     * MATCH (d:Document {doc_id: $docId})-[:HAS_CHUNK]->(c:Chunk)
     * RETURN c ORDER BY c.index ASC
     * </pre>
     *
     * @param docId 文档 ID
     * @return 按索引排序的分块列表
     */
    @Query("MATCH (d:Document {doc_id: $docId})-[:HAS_CHUNK]->(c:Chunk) " +
           "RETURN c ORDER BY c.index ASC")
    List<Chunk> findAllByDocId(String docId);

    /**
     * 在分块内容中搜索关键�?     *
     * <p>使用 Neo4j 的全文索引进行内容搜索�?/p>
     *
     * <p>注意：需要为 Chunk.content 字段创建全文索引�?/p>
     *
     * @param keyword 要搜索的关键�?     * @return 包含该关键词的分块列�?     */
    List<Chunk> findByContentContaining(String keyword);

    /**
     * 局部检索：查找与实体相关的分块�?跳邻居）
     *
     * <p>检索逻辑�?/p>
     * <ol>
     *   <li>查找名称匹配的实体节�?/li>
     *   <li>遍历这些实体�?跳邻居（直接关系�?/li>
     *   <li>查找这些实体被提及的所有分�?/li>
     * </ol>
     *
     * <p>查询模式�?/p>
     * <pre>
     * MATCH (e:Entity) WHERE e.name IN $entities
     * WITH collect(DISTINCT e) AS seeds
     * OPTIONAL MATCH (e)-[:RELATION_TYPE*1..1]-(n:Entity)
     * WITH seeds + collect(DISTINCT n) AS nodes
     * UNWIND nodes AS ent
     * WITH ent WHERE ent IS NOT NULL
     * MATCH (ent)<-[:MENTIONS]-(ch:Chunk)
     * RETURN ch.chunk_id, ch.content, collect(DISTINCT ent.name) AS entityNames
     * </pre>
     *
     * <p>性能特征�?/p>
     * <ul>
     *   <li>遍历深度�?跳关�?/li>
     *   <li>适合：精确实体检�?/li>
     *   <li>性能：较�?/li>
     * </ul>
     *
     * @param entities 实体名称列表
     * @param limit 返回结果数量限制
     * @return 分块投影列表（包含分�?ID、内容和实体名称�?     */

    /**
     * 局部检索：查找与实体相关的分块�?跳邻居）
     *
     * <p>检索逻辑�?/p>
     * <ol>
     *   <li>查找名称匹配的实体节�?/li>
     *   <li>遍历这些实体�?跳邻居（朋友的朋朋）</li>
     *   <li>查找这些实体被提及的所有分�?/li>
     * </ol>
     *
     * <p>查询模式�?/p>
     * <pre>
     * MATCH (e:Entity) WHERE e.name IN $entities
     * WITH collect(DISTINCT e) AS seeds
     * OPTIONAL MATCH (e)-[:RELATION_TYPE*1..2]-(n:Entity)
     * WITH seeds + collect(DISTINCT n) AS nodes
     * UNWIND nodes AS ent
     * WITH ent WHERE ent IS NOT NULL
     * MATCH (ent)<-[:MENTIONS]-(ch:Chunk)
     * RETURN ch.chunk_id, ch.content, collect(DISTINCT ent.name) AS entityNames
     * </pre>
     *
     * <p>性能特征�?/p>
     * <ul>
     *   <li>遍历深度�?跳关�?/li>
     *   <li>适合：扩展实体检�?/li>
     *   <li>性能：中�?/li>
     * </ul>
     *
     * @param entities 实体名称列表
     * @param limit 返回结果数量限制
     * @return 分块投影列表（包含分�?ID、内容和实体名称�?     */

    /**
     * 全局检索：基于社区关键词查找相关分�?     *
     * <p>检索逻辑�?/p>
     * <ol>
     *   <li>在社区的 keywords、title、summary 字段中匹配关键词</li>
     *   <li>找到匹配社区的所有实�?/li>
     *   <li>找到这些实体被提及的所有分�?/li>
     * </ol>
     *
     * <p>查询模式�?/p>
     * <pre>
     * MATCH (c:Community)
     * WHERE any(k IN $keywords WHERE
     *   any(ck IN coalesce(c.keywords, []) WHERE toLower(ck) CONTAINS toLower(k))
     *   OR (c.title IS NOT NULL AND toLower(c.title) CONTAINS toLower(k))
     *   OR (c.summary IS NOT NULL AND toLower(c.summary) CONTAINS toLower(k))
     * )
     * WITH DISTINCT c
     * MATCH (c)<-[:BELONGS_TO]-(e:Entity)
     * MATCH (e)<-[:MENTIONS]-(ch:Chunk)
     * RETURN ch.chunk_id, ch.content, collect(DISTINCT e.name), collect(DISTINCT c.community_id)
     * </pre>
     *
     * <p>关键词匹配规则：</p>
     * <ul>
     *   <li>在社�?keywords 列表中模糊匹配（忽略大小写）</li>
     *   <li>在社�?title 字段中模糊匹配（忽略大小写）</li>
     *   <li>在社�?summary 字段中模糊匹配（忽略大小写）</li>
     * </ul>
     *
     * <p>性能特征�?/p>
     * <ul>
     *   <li>遍历方式：社�?-> 实体 -> 分块</li>
     *   <li>适合：主题性查�?/li>
     *   <li>性能：取决于社区数量</li>
     * </ul>
     *
     * @param keywords 关键词列�?     * @param limit 返回结果数量限制
     * @return 分块投影列表（包含分�?ID、内容、实体名称和社区 ID�?     */

    /**
     * 分块检索结果投影接�?     *
     * <p>用于定义 Cypher 查询返回的字段，避免完整加载 Chunk 实体�?/p>
     *
     * <p>优点�?/p>
     * <ul>
     *   <li>提高查询性能（只查询需要的字段�?/li>
     *   <li>减少内存占用</li>
     *   <li>避免加载不必要的关系</li>
     * </ul>
     */
}

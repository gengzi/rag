# rag-graph 系统设计文档

## 1. 目标与范围
- 解析已写入 Elasticsearch 的文本块数据，构建 Neo4j 图数据库结构与数据。
- 为 GraphRAG 检索流程提供图结构支撑，提升检索效率与上下文召回质量。
- 覆盖：ES -> 图写入、图模型、触发方式与运行流程、RAG 检索使用方式（设计）。
- 说明：Neo4j 仅存图结构与业务属性，不保存向量。

## 2. 现状概览（基于代码）
- ES 读取：`EsRagSourceService` 使用 match_all + `_id` 升序 + search_after 分页。
- 数据映射：`EsRagDocumentMapper` 将 ES 文档映射为 `RagGraphDocument` 结构。
- 图写入：`Neo4jGraphWriter` 通过 Cypher MERGE 写入图节点与关系。
- 触发方式：
  - 启动时构建（`rag.graph.on-startup`）。
  - Webhook 触发（`/webhook/build-graph`）。

## 3. 业务流程设计
1. ES 分页读取指定索引文档。
2. 文档映射为内存模型 `RagGraphDocument`（包含 chunks、mentions、entities、relations）。
3. 以幂等方式写入 Neo4j：Document -> Chunk -> Mention -> Entity -> Relation。
4. 通过 ES 向量相似度检索（阈值 > 0.8）将 Chunk 归并到社区，并写入社区节点与关系。
5. 基于社区内 Chunk 的关键词与实体信息构建 Keyword/Entity 关系。
4. GraphRAG 检索阶段：
   - 输入问题 -> 查询理解 -> 识别实体/关键词。
   - 图检索（实体+关系扩展）-> 关联 chunk。
   - 候选 chunk 回到向量/文本检索再排序 -> 生成答案（后续实现）。

## 4. 数据源与映射规则
### 4.1 ES 数据结构（约定）
- 文档层：
  - `doc_id` / `docId` / `document_id` / `id`
  - `docnm_kwd` / `title` / `doc_title` / `document_title`
  - `metadata.documentName`（备用）
  - `title_tks` / `title_sm_tks`（分词标题，备用）
  - `url` / `metadata.sourceFileUrl`
  - `create_time` / `created_at` / `metadata.createdAt`（支持 ISO 日期时间）
- chunk 层：
  - `chunks` / `chunk_list` / `chunkList`，若无则尝试用文档本身作为 chunk（当前索引按单 chunk 文档存储）。
  - `chunk_id` / `chunkId` / `id` / `doc_id`
  - `index` / `chunk_index` / `position_int` / `pageNumber` / `page_num_int` / `pageNumInt`（支持逗号分隔的页面列表，取首个）
  - `content` / `content_sm_ltks` / `content_ltks` / `text` / `chunk_text`
- mention 层：
  - `mentions` / `mention_list` / `entity_mentions`
  - `mention_id` / `mentionId` / `id`
  - `span` / `text`
  - `confidence`
- entity 层：
  - 嵌套 `entity` / `referred_entity` / `target_entity` / `entity_info` 等。
  - 若无嵌套对象，则尝试 `entity_id` / `entity_name`。
  - `aliases` 允许为 string 或 list。
- relation 层：
  - `relations` / `relation_list` / `edges`
  - `key` / `relation` / `type`
  - `description`
  - `uncertain`
  - `target` / `target_entity` / `to`
- similarity/keywords（待补充）：
  - Chunk 相似度来源：ES 向量检索（阈值 > 0.8，不落地到 Neo4j）。
  - 关键词字段：TBD（需要与 youtu graphrag 结构对齐）。

### 4.2 ID 生成策略（现状）
- doc_id：优先 ES 字段或 `metadata.documentId`，缺失则使用 ES hit id。
- chunk_id：优先字段，缺失时使用 `docId:index` 或 UUID。
- mention_id：优先字段，缺失时 UUID。
- entity id：优先字段，缺失时用 `ent:{name}` 或 UUID。

## 5. 图模型设计
### 5.1 节点类型
- `Document`：`doc_id`, `title`, `url`, `created_at`
- `Chunk`：`chunk_id`, `index`, `content`
- `Mention`：`mention_id`, `span`, `confidence`, `chunk_id`
- `Entity`：`id`, `name`, `canonical_name`, `type`, `slug`, `aliases`
- `Community`：`community_id`, `name`, `description`, `size`, `score`（与 youtu graphrag 对齐）
- `Keyword`：`keyword_id`, `name`, `weight`（与 youtu graphrag 对齐）

### 5.2 关系类型
- `(Document)-[:HAS_CHUNK]->(Chunk)`
- `(Chunk)-[:NEXT_CHUNK]->(Chunk)`（按 index 串联）
- `(Chunk)-[:HAS_MENTION]->(Mention)`
- `(Mention)-[:REFERS_TO]->(Entity)`
- `(Entity)-[:RELATION_TYPE {key, description, uncertain}]->(Entity)`
- `(Community)-[:HAS_CHUNK]->(Chunk)`（基于相似度阈值的社区归并）
- `(Community)-[:HAS_KEYWORD]->(Keyword)`
- `(Chunk)-[:HAS_KEYWORD]->(Keyword)`（可选）
- `(Chunk)-[:SIMILAR_TO {score}]->(Chunk)`（可选，若需显式保留相似度）
- `(Entity)-[:CO_OCCUR {count}]->(Entity)`（可选，用于社区内实体共现）

### 5.3 约束与索引（建议）
- 唯一约束：
  - `Document(doc_id)`
  - `Chunk(chunk_id)`
  - `Mention(mention_id)`
  - `Entity(id)`
- 常用索引：
  - `Chunk(index)`
  - `Entity(name)` / `Entity(canonical_name)`
  - `Document(title)`

## 6. 构建与触发
### 6.1 启动构建
- 配置项：
  - `rag.graph.index-name`
  - `rag.graph.batch-size`
  - `rag.graph.on-startup`
- 由 `RagGraphBuildRunner` 执行。

### 6.2 Webhook 触发
- `POST /webhook/build-graph`
- 可选参数：`indexName`、`batchSize`
- 典型用途：手动构建或定时任务调用。

## 7. GraphRAG 检索（设计）
> 该部分为后续开发规划，不代表已实现。

### 7.1 检索策略
1. Query 解析：抽取实体、关键词、时间等信号。
2. 图检索：
   - 以实体为起点做 1-2 跳扩展，获取关联实体与 mentions。
   - 聚合关联 chunk（按 mention/关系数加权）。
3. 结合向量检索：
   - 从 ES 或向量库召回相似 chunk。
4. 融合排序：
   - 图得分 + 向量得分 + 文档热度（可扩展）。

### 7.2 推荐 Cypher 模板（示例）
- 根据实体扩展相关 chunk：
  - `MATCH (e:Entity {id:$id})<-[:REFERS_TO]-(m:Mention)<-[:HAS_MENTION]-(c:Chunk) RETURN c LIMIT 50`
- 扩展关系邻居：
  - `MATCH (e:Entity {id:$id})-[:RELATION_TYPE]->(e2:Entity)<-[:REFERS_TO]-(m:Mention)<-[:HAS_MENTION]-(c:Chunk) RETURN c LIMIT 50`

## 12. 社区与关键词构建（待补充）
1. 相似度来源：ES 向量检索（只读，不写入向量到 Neo4j）。
2. 规则：对每个 Chunk 查询相似 Chunk，筛选 score > 0.8 的集合。
3. 社区归并：根据相似集合聚类（算法 TBD），为社区生成说明与指标。
4. 关键词与实体：从社区内 Chunk 的关键词/实体聚合，构建 Keyword/Entity 关系。
5. 结构对齐：具体节点/关系命名以 youtu graphrag 为准（待补充）。

## 8. 性能与可靠性
- ES 分页：使用 `_id` sort + `search_after`，避免深分页。
- 写入幂等：Neo4j 使用 MERGE，支持重复触发。
- 建议：对写入进行批处理与事务控制（后续优化点）。
- 异常处理：ES 查询异常会抛出并中断构建。

## 9. 安全与配置
- ES/Neo4j 凭据应外置并避免硬编码。
- Webhook 建议增加鉴权（token 或签名）。

## 10. 依赖与部署
- 运行环境：Spring Boot + Elasticsearch + Neo4j。
- 关键依赖：Spring Data Neo4j、Elasticsearch Java Client。

## 11. 后续规划
- 增量构建：按更新时间或游标记录增量写入。
- 图检索服务：封装 GraphRAG 查询 API。
- 图写入批量化：使用 Neo4j Batch 或 UNWIND 优化吞吐。
- 监控：写入吞吐、失败率、索引进度。

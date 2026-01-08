# Neo4j 社区发现算法实现分析

## 1. 概述

本项目使用 Neo4j Graph Data Science (GDS) 库实现了基于 Louvain 算法的社区发现功能，用于对知识图谱中的实体进行自动化的社区划分和总结报告生成。

## 2. 核心算法

### 2.1 算法选择：Louvain 算法

**实现位置**: `CommunityGraphServiceImpl.java`

**算法特点**:
- 适合大规模网络的社区检测
- 时间复杂度相对较低，接近线性
- 支持层次化社区结构
- 基于模块度优化

**核心调用**:
```cypher
CALL gds.louvain.stream($graphName, {maxLevels: 2})
YIELD nodeId, communityId, intermediateCommunityIds
```

### 2.2 算法配置参数

| 参数 | 值 | 说明 |
|------|-----|------|
| `GRAPH_NAME` | `"communityGraph"` | GDS 投影图的名称 |
| `MAX_LEVELS` | `2` | Louvain 算法的最大迭代层数，生成2层社区结构 |
| `COMMUNITY_REPORT_CHUNK_LIMIT` | `30` | 生成社区报告时使用的最大文档分块数 |
| `COMMUNITY_REPORT_MAX_CHARS` | `8000` | 生成报告时的最大字符数限制 |

## 3. 实现流程

### 3.1 整体流程

```
文档处理 → 实体提取 → 关系构建 → 图投影 → 社区发现 → 报告生成 → 知识图谱更新
```

### 3.2 详细步骤

#### 步骤1: 图投影 (`projectGraph`)

将 Neo4j 原图投影到 GDS 内存图，用于快速计算：

```cypher
CALL gds.graph.project(
  'communityGraph',                    // 图名称
  ['Entity', 'Chunk'],                 // 节点类型
  {                                     // 关系投影配置
    RELATION_TYPE: {
      type: 'RELATION_TYPE',
      orientation: 'UNDIRECTED'         // 无向图
    },
    MENTIONS: {
      type: 'MENTIONS',
      orientation: 'UNDIRECTED'         // 无向图
    }
  }
)
```

**说明**:
- 包含 `Entity`（实体）和 `Chunk`（文档分块）两种节点
- 使用无向图关系，表示实体间的双向关联
- 关系类型：
  - `RELATION_TYPE`: 实体间的语义关系
  - `MENTIONS`: 实体在文档中被提及的关系

#### 步骤2: 执行 Louvain 算法 (`rebuildCommunities`)

```cypher
CALL gds.louvain.stream('communityGraph', {maxLevels: 2})
YIELD nodeId, communityId, intermediateCommunityIds
WITH gds.util.asNode(nodeId) AS n, communityId, intermediateCommunityIds
WHERE n:Entity                         // 只处理实体节点
RETURN
  n.id AS entityId,                    // 实体ID
  CASE
    WHEN size(intermediateCommunityIds) > 0
    THEN intermediateCommunityIds[0]    // 第一层社区ID
    ELSE communityId
  END AS level1Id,
  communityId AS level2Id              // 第二层社区ID
```

**层次化结构**:
- **Level 1 (L1)**: 粗粒度社区，由 `intermediateCommunityIds[0]` 表示
- **Level 2 (L2)**: 细粒度社区，由最终的 `communityId` 表示
- 每个实体同时属于一个 L2 社区和一个 L1 社区

#### 步骤3: 清理临时图 (`dropGraphIfExists`)

```cypher
CALL gds.graph.drop('communityGraph', false)
```

**说明**:
- 在每次重新计算前删除旧的投影图
- `false` 参数表示如果图不存在也不报错

#### 步骤4: 保存社区结果 (`saveCommunities`)

**4.1 统计社区大小**

遍历所有实体的社区归属，统计每个社区的实体数量：

```java
Map<String, Integer> level1Sizes = new HashMap<>();
Map<String, Integer> level2Sizes = new HashMap<>();
// 统计逻辑...
```

**4.2 创建社区节点**

```cypher
UNWIND $rows AS row
MERGE (c:Community {community_id: row.communityId, level: row.level})
SET c.size = row.size
```

**4.3 建立实体与社区的归属关系**

```cypher
UNWIND $rows AS row
MATCH (e:Entity {id: row.entityId})
MERGE (c1:Community {community_id: row.level1Community, level: 1})
MERGE (c2:Community {community_id: row.level2Community, level: 2})
MERGE (e)-[r1:BELONGS_TO]->(c1)
SET r1.level = 1
MERGE (e)-[r2:BELONGS_TO]->(c2)
SET r2.level = 2
```

**关系说明**:
- `BELONGS_TO`: 表示实体属于某个社区
- `level` 属性区分社区层级（1 或 2）

**4.4 建立社区层级关系**

```cypher
UNWIND $rows AS row
MERGE (parent:Community {community_id: row.parentId, level: 2})
MERGE (child:Community {community_id: row.childId, level: 1})
MERGE (parent)-[:PARENT_OF]->(child)
```

**关系说明**:
- `PARENT_OF`: 表示 L2 社区包含 L1 社区
- 形成社区层次结构树

#### 步骤5: 生成社区报告 (`generateCommunityReports`)

**5.1 查询社区相关内容**

```cypher
MATCH (c:Community)
CALL {
  WITH c
  MATCH (c)<-[:BELONGS_TO]-(e:Entity)<-[:MENTIONS]-(ch:Chunk)
  WHERE ch.content IS NOT NULL AND ch.content <> ''
  RETURN collect(DISTINCT ch.content)[0..30] AS contents  // 限制最多30个分块
}
RETURN c.community_id AS communityId, c.level AS level, contents
```

**查询逻辑**:
1. 找到社区中的所有实体
2. 找到提及这些实体的文档分块
3. 收集文档内容用于生成报告

**5.2 LLM 生成报告**

使用配置的提示词模板（`COMMUNITY_REPORT_PROMPT.md`）调用 LLM：

**提示词要求**:
- 生成简短中文标题（5-10字）
- 撰写详细摘要（50-100字）
- 评估置信度（0-10分）
- 提取关键词列表

**5.3 存储报告结果**

```cypher
MATCH (c:Community {community_id: $communityId, level: $level})
SET
  c.title = $title,
  c.summary = $summary,
  c.rating = $rating,
  c.keywords = $keywords,
  c.report_updated_at = $updatedAt
```

**报告属性**:
- `title`: 社区标题
- `summary`: 社区摘要说明
- `rating`: 置信度评分（0-10）
- `keywords`: 关键词列表
- `report_updated_at`: 报告更新时间

## 4. 数据模型

### 4.1 图谱结构

```
Document -[:HAS_CHUNK]-> Chunk
Chunk -[:MENTIONS]-> Entity
Entity -[:RELATION_TYPE]-> Entity
Entity -[:BELONGS_TO]-> Community (level: 1)
Entity -[:BELONGS_TO]-> Community (level: 2)
Community (level: 2) -[:PARENT_OF]-> Community (level: 1)
```

### 4.2 节点类型

| 节点类型 | 标签 | 主要属性 |
|---------|------|---------|
| 文档 | `Document` | `id`, `title`, `content` |
| 文档分块 | `Chunk` | `id`, `content`, `index` |
| 实体 | `Entity` | `id`, `type`, `name`, `description` |
| 社区 | `Community` | `community_id`, `level`, `size`, `title`, `summary` |

### 4.3 关系类型

| 关系类型 | 说明 | 属性 |
|---------|------|------|
| `HAS_CHUNK` | 文档包含分块 | - |
| `MENTIONS` | 分块提及实体 | - |
| `RELATION_TYPE` | 实体间关系 | `type`, `description` |
| `BELONGS_TO` | 实体属于社区 | `level` (1或2) |
| `PARENT_OF` | 社区父子关系 | - |

## 5. 调用时机

### 5.1 自动触发

社区发现在 `GraphExtractionServiceImpl.extractAndSaveEntities()` 方法中自动触发：

```java
@Override
@Transactional(value = "transactionManager")
public List<EntityRelationDTO> extractAndSaveEntities(String chunkText, String... chunkId) {
    // 1. 提取实体和关系
    // 2. 保存到 Neo4j
    // 3. 重建社区结构
    communityGraphService.rebuildCommunities();

    // 4. 生成社区报告
    generateCommunityReports();

    return entityRelationDTOList;
}
```

**触发条件**:
- 每次文档分块处理完成
- 实体关系提取完成
- 知识图谱更新后

### 5.2 手动触发

可以通过 REST API 手动触发社区重建：

```java
// CommunityGraphController
@PostMapping("/rebuild")
public Response<String> rebuildCommunities() {
    communityGraphService.rebuildCommunities();
    return Response.success();
}
```

## 6. 配置说明

### 6.1 提示词配置

**配置文件**: `PromptProperties.java`

```java
@ConfigurationProperties(prefix = "prompt")
public class PromptProperties {
    private String entityExtraction = "prompt/ENTITY_EXTRACTION_PROMPT.md";
    private String communityReport = "prompt/COMMUNITY_REPORT_PROMPT.md";
}
```

**社区报告提示词**: `src/main/resources/prompt/COMMUNITY_REPORT_PROMPT.md`

提示词内容包括：
- 分析社区内的实体关系
- 总结社区主题
- 评估分析置信度
- 提取关键概念

### 6.2 LLM 配置

需要在 `application.yml` 中配置：

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4
          temperature: 0.7
```

## 7. 性能优化建议

### 7.1 当前实现的优化点

1. **GDS 投影图**: 使用内存图加速计算
2. **批量写入**: 使用 `UNWIND` 批量更新数据
3. **内容限制**: 限制输入 LLM 的内容数量（30个分块、8000字符）

### 7.2 可进一步优化的方向

1. **增量更新**: 当前每次全量重建，可改为增量更新
2. **社区合并**: 对于过小的社区进行合并处理
3. **缓存机制**: 缓存社区报告，避免重复生成
4. **并行处理**: 社区报告生成可并行化
5. **社区质量评估**: 添加社区质量评估和过滤机制

## 8. 使用示例

### 8.1 查询某个实体所属的社区

```cypher
MATCH (e:Entity {name: '人工智能'})-[:BELONGS_TO]->(c:Community)
RETURN c.community_id, c.level, c.title, c.summary
```

### 8.2 查询社区中的所有实体

```cypher
MATCH (c:Community {community_id: 'L2:123'})<-[:BELONGS_TO]-(e:Entity)
RETURN e.name, e.type
ORDER BY e.name
```

### 8.3 查询社区层次结构

```cypher
MATCH (parent:Community {level: 2})-[:PARENT_OF]->(child:Community {level: 1})
RETURN parent.community_id, parent.title, child.community_id, child.title
```

### 8.4 查询高质量社区

```cypher
MATCH (c:Community)
WHERE c.rating >= 8 AND c.size >= 5
RETURN c.community_id, c.title, c.summary, c.rating, c.size
ORDER BY c.rating DESC, c.size DESC
```

## 9. 总结

### 9.1 实现优势

1. **自动化程度高**: 无需人工干预，自动完成社区划分和报告生成
2. **层次化结构**: 2层社区结构提供多粒度的知识组织
3. **可解释性强**: 自动生成的社区报告增强知识图谱的可理解性
4. **集成度高**: 与 RAG 系统无缝集成，支持实体溯源和关系查询
5. **灵活性**: 提示词和参数可配置，适应不同场景需求

### 9.2 适用场景

- 知识图谱的自动组织和归纳
- 实体关系的聚类分析
- 文档集合的主题发现
- RAG 系统的检索优化
- 知识领域的结构化理解

### 9.3 技术栈

- **图数据库**: Neo4j
- **图算法库**: Neo4j Graph Data Science (GDS)
- **社区算法**: Louvain 算法
- **LLM**: OpenAI GPT (用于报告生成)
- **开发框架**: Spring Boot, Spring AI

---

**文档版本**: 1.0
**更新日期**: 2026-01-08
**维护团队**: RAG Graph Development Team

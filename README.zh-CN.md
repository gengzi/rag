# RAG Platform (Spring AI Multi-Module)

<p>
  <a href="./README.zh-CN.md">
    <img alt="中文文档" src="https://img.shields.io/badge/中文文档-当前页面-success?style=for-the-badge">
  </a>
  <a href="./README.en.md">
    <img alt="English Docs" src="https://img.shields.io/badge/English-Click_to_Switch-blue?style=for-the-badge">
  </a>
</p>

## 1. 项目定位

本仓库是一个面向企业知识问答与智能工作流的多模块 RAG 平台，核心目标：

- 将“知识库管理 + 检索增强问答 + Agent 协作 + 图谱检索 + MCP 工具扩展”统一在同一套工程中。
- 形成可演进的分层架构：基础能力沉淀在 `rag-core`，业务编排与场景落在 `rag-chat` / `rag-agent` / `rag-graph` / `rag-mcp`。
- 支持从 MVP 到生产化的持续演化（模块化拆分、可替换模型、可替换向量后端、可替换编排链路）。

## 2. 仓库结构与工程关系

### 2.1 模块总览

| 模块 | 角色 | 默认端口 | 关键职责 |
|---|---|---:|---|
| `rag-core` | 核心能力层 | `8883` | 文档入库、切分、Embedding、向量检索、RAG 对话、评估、用户与知识库管理 |
| `rag-chat` | 聊天业务层 | `8086` | 统一聊天入口、SSE 输出、Agent 路由、消息持久化与恢复 |
| `rag-agent` | Agent 执行层 | `8889` | AIPPT 生成、图流程执行、人类反馈节点恢复、Agent 对话补充接口 |
| `rag-graph` | 图谱增强层 | `8199` | ES→Neo4j 图构建、图知识查询、GraphRAG 检索入口 |
| `rag-mcp` | MCP 服务层 | `8890` | 用户记忆存储/检索、MCP Server、工具化能力输出 |
| `rag-mcp-client` | MCP 客户端层 | `8891` | MCP 工具编排调用示例、Prompt + Tool Callback 组合 |
| `rag-agent-teams` | 多 Agent 团队协作样例 | `8080` | Team/Task/Message 协作、自然语言编排、SSE 工作流事件 |
| `rag-serach` | 检索与Agent扩展层 | - | 检索服务扩展、DeepResearch 节点与提示词、实验性实现 |
| `rag-common` | 公共模型层 | - | 公共 DTO/工具/常量 |
| `rag-dao` | 数据访问层 | - | 实体与仓储接口 |
| `rag-manager` | 管理扩展层 | - | 管理相关扩展（按项目演进补充） |

### 2.2 依赖分层（逻辑）

- 底层：`rag-common` + `rag-dao`
- 基础服务：`rag-core` + `rag-serach`
- 场景服务：`rag-chat`、`rag-agent`、`rag-graph`、`rag-mcp`、`rag-mcp-client`、`rag-agent-teams`

目标是把“通用可复用能力”固定在基础层，把“高变化业务流程”放在场景层。

## 3. 每个工程的设计要点

### 3.1 `rag-core`（核心 RAG 平台）

**核心职责**

- 知识库生命周期管理：知识库创建、文档上传、文档解析状态管理。
- 文档处理链路：PDF/OCR/Markdown/Excel/CSV 等多格式入库与切分。
- 向量检索与增强生成：`VectorStore` + Advisor + 会话记忆参数化。
- 对话历史与引用追溯：Conversation、Message、Reference 的统一封装。
- 评估能力：评估集创建、执行与统计接口。

**关键接口（部分）**

- `POST /chat/rag`：RAG SSE 对话输出。
- `POST /chat/search`：检索型问答输出。
- `GET /chat/rag/msg/list`：对话历史查询。
- `POST /api/knowledge-base/create`：创建知识库。
- `POST /api/knowledge-base/batch-upload`：文档批量上传。
- `POST /document/embedding`：触发文档向量化。
- `GET /document/chunks/details`：查看分块细节。

**设计特点**

- 检索过滤隔离：通过 `VectorStoreDocumentRetriever.FILTER_EXPRESSION` 按 `kbId` 做权限与租户隔离。
- RAG 上下文注入：通过 `RagChatContext` 传递 chatId / conversationId / userId，用于引用绑定和落库。
- 工具调用融合：在对话链路中注入 `DateTimeTools`，避免把事实性时间推理交给 LLM 幻觉生成。
- 对话引用闭环：从 `RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT` 获取命中文档并回传给前端。

**当前风险与优化点**

- 部分旧消息仍有 JSON 大字段遗留，需要持续迁移到“行式消息表 + 分页游标”。
- Prompt 字符串较长且硬编码，建议迁移到模板与版本化配置。

### 3.2 `rag-chat`（业务编排聊天服务）

**核心职责**

- 统一聊天入口，承接 Agent 与普通 RAG 两类会话。
- SSE 分片输出、分片合并、断线恢复。
- 对话消息与会话记录持久化。
- 通过 Redisson 分布式锁控制同会话并发写入。

**关键接口（部分）**

- `POST /chat/stream/msg`：聊天流输出。
- `POST /chat/msg/{conversationId}/list`：按游标分页拉取历史。
- `POST /chat/stream-tts`：文本转音频流输出。

**设计特点**

- 分布式互斥：锁键 `chat:message:lock:{conversationId}`，避免同会话并发写入导致乱序。
- 流式可恢复：消息片段进入 Redis Stream + Hash 映射序号，前端可按 `messageId + seqNum` 续拉。
- 多 Agent 路由：根据 `agentId` 分发到 DeepResearch/PPT/Excalidraw/Text2SQL 等执行链。
- 结果聚合策略：同类型片段合并（`LlmTextRes`、`AgentGraphRes` 等），降低前端渲染负担。

**当前风险与优化点**

- 分片合并规则在服务内硬编码，建议抽象为策略接口并补全测试。
- Redis Stream 的消费窗口与清理策略需完善，避免长周期堆积。

### 3.3 `rag-agent`（AIPPT 与图流程执行）

**核心职责**

- 基于阿里 Spring AI Graph 执行 AIPPT 生成流。
- 支持人类反馈中断点恢复（`resume`）。
- 提供 PPT 母版解析与页面内容生成服务。

**关键接口（部分）**

- `POST /aippt/generate`：同步生成。
- `POST /aippt/generateStream`：流式生成。
- `POST /aippt/resume`：基于会话状态恢复。
- `POST /aippt/ppt/motherboadr/parse`：母版解析。

**设计特点**

- 图状态持久化抽象：使用 `CompiledGraph` + `MemorySaver`，具备切换 checkpoint saver 的基础。
- 节点流转可观测：通过 `GraphProcess.processStream` 将节点输出统一映射为 SSE 事件。
- “内容生成”与“版式处理”分层：`AiPPTContentGenerationService` 与 `PptGenerationService` 分责。

**当前风险与优化点**

- 目前 checkpoint 使用内存实现，生产建议切换持久化 saver。
- 母版解析链路建议增加失败重试和模板校验报告。

### 3.4 `rag-graph`（图谱构建与图检索）

**核心职责**

- 从 Elasticsearch 分页拉取文档并构建 Neo4j 图结构。
- 提供图知识查询与 GraphRAG 检索能力入口。
- 支持启动构建和 webhook 构建两种触发模式。

**关键接口（部分）**

- `POST /webhook/build-graph`：触发图构建。
- `POST /api/graph/retrieve`：图检索接口。
- `POST /api/graph/documents`：写入图文档。
- `GET /api/graph/trace`：按实体溯源文档。
- `GET /api/graph/entities/{name}`：查询实体关系。

**设计特点**

- ES 深分页友好：`search_after + _id sort`。
- Neo4j 幂等写入：`MERGE` 模式，支持重复构建。
- 图模型分层：Document/Chunk/Mention/Entity/Community/Keyword。
- 向量不入图：Neo4j 仅存图结构与业务属性，向量继续留在 ES/OpenSearch。

**当前风险与优化点**

- 批量写入仍有优化空间，后续建议 UNWIND 批处理。
- 图社区发现算法仍有演进空间（阈值、聚类与解释性策略）。

### 3.5 `rag-mcp`（MCP Server + 长期记忆）

**核心职责**

- 提供用户记忆的存储和检索接口。
- 提供 MCP Server（SSE + Message endpoint）用于外部/内部工具调用。
- 记忆抽取与异步写入的能力承载。

**关键接口（部分）**

- `POST /api/memory/store`：存储记忆。
- `GET /api/memory/search/{userId}`：检索记忆。

**设计特点**

- MCP 原生接入：`spring-ai-starter-mcp-server(-webmvc)`。
- 与向量后端解耦：可从 ES 迁移到 OpenSearch 方案。
- 记忆能力与主对话链路解耦，降低主链延迟。

### 3.6 `rag-mcp-client`（MCP 客户端示例）

**核心职责**

- 作为工具调用聚合端，演示 `ChatClient + ToolCallbacks + MCP` 的组合。
- 提供多工具场景调用模板，便于后续扩展。

**关键接口（部分）**

- `POST /mcp/store`：以 MCP 工具回调执行复合查询/写入。

**设计特点**

- `SyncMcpToolCallbackProvider` 动态注入工具能力。
- Prompt 与工具调用协同，适合构建“无感工具调用”的产品体验。

### 3.7 `rag-agent-teams`（多 Agent 协作样例）

**核心职责**

- Team/Task/Message 三元协作模型。
- 任务依赖、领取、完成与自动调度。
- 自然语言控制面（NL）+ SSE 工作流事件输出。

**关键接口（部分）**

- `POST /api/teams`：创建团队。
- `POST /api/teams/{teamId}/tasks`：创建任务。
- `POST /api/teams/{teamId}/tasks/{taskId}/run`：执行任务。
- `POST /api/teams/nl/chat/stream`：自然语言流式编排。

**设计特点**

- DDD 风格聚合：`TeamWorkspace` 作为聚合根。
- 细粒度并发控制：`synchronized(team)` 保证团队内一致性。
- LLM 重试策略：指数退避，处理限流或临时错误。

### 3.8 `rag-serach`（检索扩展与深度研究）

**核心职责**

- 提供检索增强服务扩展实现。
- 承载 DeepResearch 节点编排、提示词与实验链路。

**设计特点**

- 与 `rag-core` 保持接口兼容，便于按场景切换实现。
- 提示词与节点配置集中在 `resources/prompts`，便于 A/B 实验。

### 3.9 `rag-common` / `rag-dao` / `rag-manager`

- `rag-common`：公共对象、工具、上下文等横切能力。
- `rag-dao`：实体与仓储抽象，支撑多上层服务。
- `rag-manager`：管理能力扩展位，承接未来后台管理逻辑。

## 4. 关键业务流程

### 4.1 知识库文档入库流程

1. 前端上传文档到知识库。
2. 服务写入对象存储（S3/MinIO）。
3. 文档解析（PDF/OCR/Markdown 等）。
4. 分块、元数据补全、Embedding。
5. 写入向量索引（ES/OpenSearch）。
6. 可选：触发 `rag-graph` 构建图谱结构。

### 4.2 RAG 对话流程

1. 鉴权后进入聊天入口。
2. 根据用户可见知识库生成 `kbId` 过滤表达式。
3. 构建 `RagChatContext` 并注入会话参数。
4. 模型流式输出 + 检索引用回传。
5. 历史消息与引用落库，前端增量渲染。

### 4.3 Agent 路由流程

1. 解析请求中的 `agentId`。
2. 路由到 DeepResearch / PPT / Excalidraw / Text2SQL。
3. 统一转换为 SSE 输出格式。
4. 结果分片合并后持久化。

### 4.4 图谱构建与查询流程

1. 从 ES 批量拉取文档。
2. 映射为图文档模型。
3. Neo4j 幂等写入节点与关系。
4. 社区归并与关键实体聚合。
5. 查询阶段通过图扩展召回候选 Chunk。

### 4.5 长期记忆流程（MCP）

1. 抽取对话中的可记忆信息。
2. 存入记忆库并向量化。
3. 后续对话按用户与语义召回记忆。
4. 记忆片段注入 Prompt 形成个性化回答。

## 5. 数据与存储设计

### 5.1 关系型存储（MySQL）

- 用户、知识库、文档、会话、消息、评估数据。
- 推荐将消息表迁移为“行式存储 + 游标分页”，避免 JSON 大字段膨胀。

### 5.2 向量存储（ES/OpenSearch）

- 文档块、向量字段、检索元数据。
- 通过 metadata 保存 `kbId/userId/documentId` 实现权限与追溯。

### 5.3 图存储（Neo4j）

- 存储结构关系，不存向量。
- 支持实体关系探索、溯源和图检索。

### 5.4 对象存储（MinIO/S3）

- 原始文档、解析产物、图片资源。

### 5.5 缓存与流（Redis）

- 分布式锁（同会话串行写）。
- SSE 分片流与断点续传。

## 6. 安全与多租户隔离

- JWT 认证 + Spring Security。
- 检索时按用户可访问知识库 ID 过滤，避免跨库串读。
- 建议把所有密钥迁移到环境变量或密钥管理系统。
- 对外 webhook 和 mcp 接口建议补齐鉴权策略（token/signature）。

## 7. 可观测性与运维要点

- 各模块已配置独立日志输出，可按服务维度采集。
- 建议补齐指标：
  - 检索耗时、命中率、无结果率
  - LLM 首 token 延迟、完成耗时、重试次数
  - 文档解析成功率、图构建吞吐
- 建议补齐链路追踪：conversationId / chatId / documentId 贯通。

## 8. 工程实践建议（下一步）

1. 配置治理：把长 Prompt 与模型参数从代码移到模板中心。
2. 测试治理：补齐集成测试（SSE、并发锁、检索过滤正确性）。
3. 数据治理：消息存储全面行式化，清理历史 JSON 大字段。
4. 架构治理：抽象 Agent 结果合并策略，减少业务耦合。
5. 生产治理：记忆服务、图构建服务独立伸缩。

## 9. 启动方式

```bash
./gradlew clean build -x test
./gradlew :rag-core:bootRun
./gradlew :rag-chat:bootRun
./gradlew :rag-agent:bootRun
./gradlew :rag-graph:bootRun
./gradlew :rag-mcp:bootRun
./gradlew :rag-mcp-client:bootRun
./gradlew :rag-agent-teams:bootRun
```

## 10. 深入文档索引

- `rag-agent-teams/docs/DESIGN.md`
- `rag-agent-teams/docs/ARCHITECTURE.md`
- `rag-graph/docs/SYSTEM_DESIGN.md`
- `doc/工程实践优化.md`
- `doc/功能.md`

## 11. 注意事项

- 当前仓库中的 `application*.yml` 存在示例密钥与口令字段，请在本地或部署前替换。
- `rag-serach` 是历史命名，目录名暂未更正，代码中已按该名称引用。

## 12. 版本说明

本文档是“工程级 README 增强版（中文）”，重点覆盖：

- 每个工程的职责边界
- 关键设计要点
- 主要流程与数据路径
- 生产化落地建议

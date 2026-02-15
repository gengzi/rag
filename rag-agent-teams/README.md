# Spring AI Agent Teams (Demo)

该项目实现了一个基于 Spring Boot 的 AI 代理团队协作系统，核心架构参考 Claude Agent Teams 思路：

## 核心特性

- **多代理协作**：支持创建具有不同角色的 AI 代理团队
- **任务依赖管理**：DAG（有向无环图）式任务依赖关系，支持动态调整
- **智能任务调度**：自动负载均衡和角色感知的任务分配
- **消息传递机制**：代理间通过共享邮箱异步通信
- **上下文感知执行**：多源上下文注入（依赖输出、信箱消息、历史记录）
- **LLM 弹性调用**：指数退避重试机制（429/403 错误）
- **双接口支持**：REST API + 自然语言接口
- **动态计划调整**：Leader 可在执行过程中动态添加/更新/删除任务
- **自动 Leader 创建**：团队缺少 Leader 时自动创建协调者角色
- **实时流式反馈**：SSE（Server-Sent Events）工作流事件流

## 系统架构

### 分层结构

- **API Layer**: REST API 控制器 + 自然语言控制器
- **Service Layer**: 团队注册管理 + 任务执行编排 + LLM 重试服务
- **Domain Layer**: 团队工作空间（聚合根） + 任务/代理实体 + 消息值对象

### 核心概念

- **TeamWorkspace（聚合根）**：管理整个团队的一致性边界
- **TeamTask（任务实体）**：支持依赖关系和动态修改
- **TeammateAgent（代理实体）**：具有角色、记忆和消息处理能力
- **TeamMessage（值对象）**：代理间通信的不可变消息载体

## 1. 运行前准备

- JDK 21+
- 可用的 Gradle（或自行生成 `gradlew`）
- OpenAI API Key

环境变量：

```powershell
$env:OPENAI_API_KEY="your-key"
```

启动：

```powershell
gradle bootRun
```

## 2. 主要 API

### 创建团队

`POST /api/teams`

```json
{
  "name": "Market Analysis Team",
  "objective": "调研北美 AI Agent 产品机会并输出结论",
  "teammates": [
    { "name": "Alice", "role": "Researcher", "model": "gpt-4o-mini" },
    { "name": "Bob", "role": "Analyst", "model": "gpt-4o-mini" }
  ]
}
```

**注意**：如果团队中没有 Leader 角色，系统会自动创建 "Team Leader" 角色。

### 创建任务

`POST /api/teams/{teamId}/tasks`

```json
{
  "title": "收集竞品信息",
  "description": "列出 5 个竞品并总结定位",
  "dependencies": [],
  "assigneeId": "teammate-id"
}
```

### 更新任务计划

`PATCH /api/teams/{teamId}/tasks/{taskId}`

```json
{
  "title": "更新后的标题",
  "description": "更新后的描述",
  "dependencies": ["task-id-1", "task-id-2"],
  "assigneeId": "teammate-id"
}
```

**约束**：
- 只能更新状态为 PENDING 的任务
- 不能创建自依赖
- 依赖任务必须存在

### 删除任务计划

`DELETE /api/teams/{teamId}/tasks/{taskId}`

**约束**：
- 只能删除状态为 PENDING 的任务
- 任务不能被其他活跃任务引用

### Claim 任务

`POST /api/teams/{teamId}/tasks/{taskId}/claim`

```json
{
  "teammateId": "teammate-id"
}
```

### 发送消息

`POST /api/teams/{teamId}/messages`

```json
{
  "fromId": "teammate-a",
  "toId": "teammate-b",
  "content": "我已经完成竞品列表，请基于此做 SWOT"
}
```

### 执行任务（调用 Spring AI）

`POST /api/teams/{teamId}/tasks/{taskId}/run`

```json
{
  "teammateId": "teammate-id"
}
```

**自动分配**：如果不指定 `teammateId`，系统会自动选择负载最轻的成员（排除 Leader）。

### 查询团队状态

`GET /api/teams/{teamId}`

返回完整的团队信息，包括：
- 成员列表
- 任务列表（含依赖关系、状态、执行人）
- 最近的消息
- **计划版本号**（planVersion）

### 自然语言接口

**同步聊天**：
`POST /api/teams/nl/chat`

```json
{
  "sessionId": "session-123",
  "teamId": "team-456",
  "message": "帮我创建一个市场分析团队，并收集5个竞品信息"
}
```

**流式聊天（SSE）**：
`POST /api/teams/nl/chat/stream`

返回实时工作流事件，包括：
- 意图解析
- 动作执行
- 任务状态变化
- Leader 决策
- 用户输入等待

## 3. 代码结构

```
src/main/java/com/gengzi/agentteams/
├── AgentTeamsApplication.java           # Spring Boot 入口
├── api/
│   ├── AgentTeamsController.java         # REST API 接口
│   ├── AgentTeamsNaturalLanguageController.java  # 自然语言接口
│   ├── *Request.java                    # 请求 DTO
│   └── TeamStateResponse.java          # 响应 DTO
├── domain/
│   ├── TeamWorkspace.java              # 聚合根（含 planVersion）
│   ├── TeamTask.java                   # 任务实体（支持动态修改）
│   ├── TeammateAgent.java              # 代理实体
│   ├── TeamMessage.java                # 消息值对象
│   └── TaskStatus.java                 # 状态枚举
├── service/
│   ├── TeamRegistryService.java        # 状态管理（含 update/delete 计划）
│   ├── AgentTaskRunnerService.java     # 任务执行编排
│   └── LlmRetryService.java           # LLM 重试机制
└── nl/
    ├── NlAgentTeamsService.java       # 自然语言处理
    ├── NlIntent.java                  # 意图结构
    ├── NlWorkflowEvent.java            # 工作流事件
    └── NlSessionStore.java            # 会话管理

src/main/resources/
└── application.yml                     # 应用配置
```

### 核心类说明

#### TeamWorkspace（聚合根）
- 管理团队所有成员、任务和消息
- **planVersion 字段**：追踪计划变更历史
- **bumpPlanVersion()**：每次计划变更时自增

#### TeamTask（任务实体）
- 支持依赖关系和状态转换
- **动态修改**：可更新标题、描述、依赖关系
- **自动时间戳**：任何修改都更新 updatedAt

#### TeamRegistryService
- **updateTaskPlan()**：更新待执行任务
- **deleteTaskPlan()**：删除待执行任务
- **ensureLeaderPresent()**：自动确保团队有 Leader
- **细粒度锁**：Team 级别并发控制

#### NlAgentTeamsService
- **Leader 动态调整**：执行过程中可添加/更新/删除任务
- **用户输入暂停**：Leader 可请求用户介入
- **最多 200 轮**：防止无限循环

## 4. 设计文档

### 4.1 完整设计文档

📚 **详细设计文档**: [docs/DESIGN.md](docs/DESIGN.md)

包含以下内容：
- 🏗️ **架构设计**: 分层架构、模块组织、部署架构
- 💻 **核心代码设计**: 领域模型、服务层、代码示例
- 🔄 **流程设计**: REST API 流程、任务依赖解析、自动执行循环
- 📊 **流程图**: Mermaid 时序图、状态图、流程图
- 🎨 **设计模式**: DDD 模式、并发模式、行为模式
- 🔧 **技术栈**: 依赖说明、配置管理、API 映射
- 🚀 **扩展规划**: 短期改进、长期架构演进
- 🆕 **最新特性**: 动态任务调整、自动 Leader 创建、计划版本追踪

### 4.2 架构概览

📐 **架构概览文档**: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

快速了解项目架构的核心设计：
- 📐 分层架构图
- 🏛️ 核心领域模型（含 planVersion）
- 🔄 关键流程图（时序图、状态机、依赖解析）
- 🎯 设计模式速查
- 🔧 技术栈总结

### 4.3 快速链接

**设计文档**:
- [项目概述](docs/DESIGN.md#1-项目概述)
- [架构设计](docs/DESIGN.md#2-架构设计)
- [领域模型](docs/DESIGN.md#31-领域模型-domain-layer)
- [任务执行流程](docs/DESIGN.md#41-rest-api-任务执行流程)
- [关键设计模式](docs/DESIGN.md#5-关键设计模式)
- [配置说明](docs/DESIGN.md#7-配置说明)

**架构概览**:
- [分层架构](docs/ARCHITECTURE.md#分层架构)
- [核心领域模型](docs/ARCHITECTURE.md#核心领域模型)
- [关键流程图](docs/ARCHITECTURE.md#关键流程图)
- [设计模式](docs/ARCHITECTURE.md#设计模式)

### 4.4 关键特性说明

#### 动态任务调整
系统支持在执行过程中动态调整任务计划：
- **添加任务**：Leader 可根据执行情况动态添加新任务
- **更新任务**：修改待执行任务的标题、描述、依赖关系或执行人
- **删除任务**：移除不再需要的任务
- 所有操作都会更新 `planVersion`，便于追踪变更历史

#### 自动 Leader 创建
如果创建团队时没有指定 Leader 角色，系统会自动创建：
- 角色名称：Team Leader
- 角色类型：Leader
- 确保团队始终有协调者进行任务编排

#### 计划版本追踪
每个团队维护一个 `planVersion` 计数器：
- 创建任务时 +1
- 更新任务时 +1
- 删除任务时 +1
- 便于日志追踪和状态同步


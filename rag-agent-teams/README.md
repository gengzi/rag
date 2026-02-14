# Spring AI Agent Teams (Demo)

该项目在当前目录实现了一个 `Agent Teams` 最小可运行版本，核心结构参考 Claude Agent Teams 思路：

- `Team Lead`：通过 API 编排任务与执行
- `Teammates`：每个队友有独立角色与上下文历史
- `Shared Task List`：支持任务依赖、claim、complete
- `Mailbox`：队友之间可发消息，执行任务时自动读取未读消息

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

### 查询团队状态

`GET /api/teams/{teamId}`

## 3. 代码结构

- `src/main/java/com/gengzi/AgentTeamsApplication.java`
- `src/main/java/com/gengzi/agentteams/api/AgentTeamsController.java`
- `src/main/java/com/gengzi/agentteams/service/TeamRegistryService.java`
- `src/main/java/com/gengzi/agentteams/service/AgentTaskRunnerService.java`
- `src/main/resources/application.yml`

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

### 4.2 架构概览

📐 **架构概览文档**: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

快速了解项目架构的核心设计：
- 📐 分层架构图
- 🏛️ 核心领域模型
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


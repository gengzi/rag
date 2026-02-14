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


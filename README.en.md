# RAG Platform (Spring AI Multi-Module)

<p>
  <a href="./README.zh-CN.md">
    <img alt="中文文档" src="https://img.shields.io/badge/中文文档-点击切换-brightgreen?style=for-the-badge">
  </a>
  <a href="./README.en.md">
    <img alt="English Docs" src="https://img.shields.io/badge/English-Current_Page-success?style=for-the-badge">
  </a>
</p>

## Overview

This repository is a multi-module Spring AI based RAG platform with:

- Knowledge base ingestion and retrieval
- Streaming chat and agent routing
- Graph-enhanced retrieval (Neo4j)
- MCP server/client based memory/tool integration
- Team-style multi-agent orchestration demo

## Modules

| Module | Role | Port |
|---|---|---:|
| `rag-core` | Core RAG capability layer | `8883` |
| `rag-chat` | Chat orchestration and streaming layer | `8086` |
| `rag-agent` | AIPPT and graph-flow agent execution | `8889` |
| `rag-graph` | Graph build + graph retrieval | `8199` |
| `rag-mcp` | MCP server + long-term memory service | `8890` |
| `rag-mcp-client` | MCP client integration sample | `8891` |
| `rag-agent-teams` | Multi-agent team collaboration demo | `8080` |
| `rag-serach` | Retrieval/deep-research extension module | - |
| `rag-common` / `rag-dao` / `rag-manager` | Shared and supporting layers | - |

## Design Highlights

- Strict module separation: reusable core vs scenario-specific services.
- SSE-first interaction model for chat/agent streams.
- Retrieval isolation by knowledge-base scope (`kbId` filtering).
- Redis-based stream replay and distributed lock for conversation consistency.
- GraphRAG-ready model: ES vector retrieval + Neo4j relationship graph.
- MCP-native tool/memory extension path.

## Quick Start

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

## Detailed Docs

- Chinese full project architecture: `README.zh-CN.md`
- Agent Teams design: `rag-agent-teams/docs/DESIGN.md`
- Agent Teams architecture: `rag-agent-teams/docs/ARCHITECTURE.md`
- Graph system design: `rag-graph/docs/SYSTEM_DESIGN.md`

## Security Note

Some `application*.yml` files currently include example secrets/password fields. Replace them before real deployment.

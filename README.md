# RAG Platform (Spring AI Multi-Module)

<p>
  <a href="./README.zh-CN.md">
    <img alt="中文文档" src="https://img.shields.io/badge/中文文档-点击切换-brightgreen?style=for-the-badge">
  </a>
  <a href="./README.en.md">
    <img alt="English Docs" src="https://img.shields.io/badge/English-Click_to_Switch-blue?style=for-the-badge">
  </a>
</p>

Default language: Chinese. Click the button above to switch.

默认语言：中文。点击上方按钮切换语言。

## What This Repo Contains

- Multi-module Spring AI RAG platform
- Knowledge ingestion + vector retrieval + chat memory
- Agent orchestration (DeepResearch / PPT / Excalidraw / Text2SQL)
- Graph-enhanced retrieval (Neo4j)
- MCP server/client integration for tool and memory extension

## Read In Order

1. Full Chinese architecture guide: `README.zh-CN.md`
2. English quick architecture guide: `README.en.md`
3. Agent Teams design deep dive: `rag-agent-teams/docs/DESIGN.md`
4. Graph subsystem design deep dive: `rag-graph/docs/SYSTEM_DESIGN.md`

## Module Ports

- `rag-core`: `8883`
- `rag-chat`: `8086`
- `rag-agent`: `8889`
- `rag-graph`: `8199`
- `rag-mcp`: `8890`
- `rag-mcp-client`: `8891`
- `rag-agent-teams`: `8080`

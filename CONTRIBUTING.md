# Contributing

Thanks for your interest in contributing to `gengzi/rag`.

This project is a Spring AI based multi-module RAG and Agent platform. It is currently in an early engineering stage, so documentation, examples, tests, and issue reports are all valuable contributions.

## Project Scope

The repository focuses on practical Java / Spring AI implementation patterns for:

- Knowledge ingestion and vector retrieval
- Streaming RAG chat with SSE
- Conversation persistence and recovery
- Agent routing and graph workflow execution
- GraphRAG with Neo4j
- MCP server/client integration
- Long-term memory and tool extension
- Multi-agent team collaboration examples

## How to Get Started

1. Fork the repository.
2. Create a feature branch from `main`.
3. Read the module overview in `README.zh-CN.md` or `README.en.md`.
4. Choose the module you want to improve.
5. Run a local build before opening a pull request.

```bash
./gradlew clean build -x test
```

## Module Boundaries

Please keep changes aligned with the current module responsibilities:

| Module | Responsibility |
|---|---|
| `rag-core` | Core RAG capabilities, document ingestion, vector retrieval, knowledge-base management |
| `rag-chat` | Chat orchestration, SSE streaming, message persistence, Agent routing |
| `rag-agent` | Agent graph execution, AIPPT generation, human feedback resume |
| `rag-graph` | ES/OpenSearch to Neo4j graph build, GraphRAG retrieval |
| `rag-mcp` | MCP server and long-term memory service |
| `rag-mcp-client` | MCP client integration examples |
| `rag-agent-teams` | Multi-agent team collaboration sample |
| `rag-common` / `rag-dao` | Shared models, utilities, persistence abstractions |

## Good First Contributions

Good first contributions include:

- Improving the quickstart documentation
- Adding Docker Compose examples
- Adding sample documents and demo scripts
- Adding integration tests for RAG retrieval
- Adding tests for SSE stream recovery
- Moving hardcoded prompts into versioned prompt templates
- Improving configuration examples
- Adding architecture diagrams
- Fixing typos and improving English documentation

## Issue Guidelines

When opening an issue, please include:

- What you are trying to do
- Which module is involved
- Expected behavior
- Actual behavior
- Relevant logs or screenshots
- Environment details, such as Java version, database, Redis, ES/OpenSearch, Neo4j, and OS

## Pull Request Guidelines

Before opening a pull request:

- Keep the PR focused on one topic.
- Explain why the change is needed.
- Include tests or a manual verification note when possible.
- Update documentation if behavior or setup changes.
- Keep local private configuration out of commits.

## Code Style

- Prefer clear module boundaries over cross-module shortcuts.
- Keep reusable logic in lower-level modules when possible.
- Keep scenario-specific orchestration in `rag-chat`, `rag-agent`, `rag-graph`, `rag-mcp`, or `rag-agent-teams`.
- Prefer explicit DTOs and clear naming for API contracts.
- Add comments only when they explain non-obvious design choices.

## Maintainer Notes

The project is maintained as an engineering-oriented OSS reference for Java / Spring AI teams. Issues and PRs that improve runnability, tests, documentation, configuration, or production readiness are especially welcome.

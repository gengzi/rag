# Changelog

All notable changes to this project will be documented in this file.

This project follows an early-stage release process. Version numbers may change quickly before the first stable release.

## [Unreleased]

### Planned

- Docker Compose quickstart for local development.
- Integration tests for RAG retrieval and SSE recovery.
- Persistent checkpoint saver for `rag-agent` workflows.
- Prompt template versioning and prompt governance.
- Observability metrics for retrieval, LLM calls, document parsing, and graph build.
- More MCP memory and tool integration examples.

## [0.1.0-alpha] - Pending

### Added

- Multi-module Spring AI RAG platform structure.
- `rag-core` for knowledge ingestion, document chunking, vector retrieval, RAG chat, and evaluation.
- `rag-chat` for unified chat entry, SSE streaming, Agent routing, message persistence, and recovery direction.
- `rag-agent` for AIPPT generation, graph workflow execution, and human feedback resume direction.
- `rag-graph` for ES/OpenSearch to Neo4j graph build and GraphRAG retrieval.
- `rag-mcp` for MCP server and long-term memory service direction.
- `rag-mcp-client` for MCP client integration examples.
- `rag-agent-teams` for Team/Task/Message based multi-agent collaboration examples.
- Architecture overview with Mermaid diagrams.
- Roadmap and contribution guide.

### Known Limitations

- The project is still in an early engineering stage.
- Local one-click startup still needs improvement.
- Test coverage and observability are planned but not complete.
- Some production-readiness work is tracked in GitHub Issues and `ROADMAP.md`.

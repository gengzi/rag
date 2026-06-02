# Roadmap

This roadmap describes the near-term evolution plan for `gengzi/rag` as an open-source Spring AI based RAG and Agent platform.

The project is currently in an early engineering stage. The goal is to make it easier for Java / Spring backend teams to learn, run, extend, and productionize RAG and Agent applications.

## v0.1.0-alpha: Runnable Engineering Baseline

Focus: make the existing multi-module project easier to understand and run.

- [ ] Publish the first GitHub Release: `v0.1.0-alpha`.
- [ ] Add a minimal local quickstart for the core modules.
- [ ] Document required middleware: MySQL, Redis, ES/OpenSearch, Neo4j, MinIO/S3.
- [ ] Provide example environment variables and remove hardcoded local secrets from examples.
- [ ] Add architecture overview diagrams for RAG, Agent, GraphRAG, MCP, and multi-agent teams.

## v0.2.0: One-Click Local Development

Focus: reduce setup cost for external users and contributors.

- [ ] Add Docker Compose for local middleware startup.
- [ ] Add sample documents and demo knowledge base bootstrap scripts.
- [ ] Provide end-to-end demo scripts for:
  - [ ] Knowledge ingestion
  - [ ] RAG chat
  - [ ] SSE chat recovery
  - [ ] Agent routing
  - [ ] GraphRAG retrieval
  - [ ] MCP memory search
- [ ] Add troubleshooting documentation for common startup issues.

## v0.3.0: Tests, Evaluation, and Observability

Focus: improve maintainability and production confidence.

- [ ] Add integration tests for RAG retrieval and knowledge-base filtering.
- [ ] Add tests for SSE chunk merge and recovery behavior.
- [ ] Add tests for Redis distributed locking in conversation writes.
- [ ] Add RAG evaluation examples: hit rate, no-result rate, answer relevance, citation correctness.
- [ ] Add basic metrics for LLM latency, retrieval latency, document parsing success rate, and graph build throughput.

## v0.4.0: Agent and Workflow Production Readiness

Focus: improve the Agent execution layer and workflow reliability.

- [ ] Replace in-memory graph checkpoint saver with persistent storage.
- [ ] Abstract Agent output merge rules into strategy interfaces.
- [ ] Move long prompts and model parameters into versioned prompt templates.
- [ ] Add failure retry and validation reports for PPT template parsing.
- [ ] Add examples for human-in-the-loop resume flows.

## v0.5.0: MCP, Memory, and Extension Ecosystem

Focus: make tools and memory reusable across applications.

- [ ] Add more MCP server examples.
- [ ] Add memory extraction and async write examples.
- [ ] Add permission and tenant isolation examples for tool calls.
- [ ] Provide a plugin-style extension guide for adding new tools and agents.
- [ ] Add examples for Java/Spring AI teams to integrate this project into existing backend systems.

## Long-Term Direction

- Enterprise-grade Java RAG platform reference implementation.
- Spring AI based Agent orchestration examples.
- GraphRAG and MCP integration for production-oriented AI applications.
- Multi-agent collaboration examples that can be reused by backend teams.
- Practical engineering patterns for SSE, message persistence, checkpoint recovery, and tool-augmented LLM workflows.

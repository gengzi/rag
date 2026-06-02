# Security Policy

## Supported Versions

`gengzi/rag` is currently in an early alpha engineering stage. Security fixes and hardening work will focus on the latest `main` branch and the latest published release.

| Version | Supported |
|---|---|
| `main` | Yes |
| `v0.1.0-alpha` and later | Yes |

## Reporting a Vulnerability

If you discover a security issue, please avoid publishing exploit details directly in a public issue.

Recommended reporting path:

1. Open a GitHub issue with a high-level description and mark it as security-related, without including sensitive exploit details.
2. Provide affected module information, such as `rag-core`, `rag-chat`, `rag-agent`, `rag-graph`, `rag-mcp`, or `rag-agent-teams`.
3. Include safe reproduction context where possible.

## Security Areas We Care About

The project is especially interested in reports related to:

- Knowledge-base access isolation
- Cross-tenant retrieval leakage
- Prompt injection against RAG or Agent workflows
- Unsafe tool execution
- MCP tool exposure boundaries
- SSE message replay and authorization
- File upload and document parsing risks
- Sensitive configuration handling

## Current Security Notes

This project is not yet a production-ready security baseline. Before real deployment, users should review:

- Authentication and authorization configuration
- Knowledge-base permission filtering
- External webhook protection
- MCP endpoint exposure
- File upload validation
- Network access boundaries
- Local configuration values

Security hardening tasks are tracked in `ROADMAP.md` and GitHub Issues.

# Plan — MCP-STACK-SETUP

**task_id:** MCP-STACK-SETUP  
**title:** Configure Context7, Next DevTools, DBHub PostgreSQL, Confluent Kafka MCP  
**lane:** FAST  
**source:** user request — Debina MCP stack setup  

## Goal

Configure project MCP servers (Context7 OAuth, Next.js DevTools, read-only PostgreSQL via DBHub, read-only Kafka via Confluent MCP), update Cursor rules and manual setup documentation. No production code, migrations, or secrets in repo.

## Allowed paths

- `.cursor/mcp.json`
- `.cursor/rules/**`
- `tools/mcp/**`
- `docs/governance/**`
- `work/active/MCP-STACK-SETUP/**`
- `work/ACTIVE.json`

## Steps

1. Audit local Java/Spring/Next.js/Kafka/PostgreSQL versions from repo and containers.
2. Add `.cursor/mcp.json` with four MCP server entries.
3. Add `tools/mcp/start-dbhub.sh` and example configs under `docs/governance/mcp/`.
4. Update `00-core`, `20-backend`, `30-database`, add `25-frontend` rules.
5. Extend `docs/governance/CURSOR-LEAN-HARNESS-MANUAL-SETUP.md` with MCP manual steps.
6. Run validation commands (json.tool, grep, diff --check, cursor-agent mcp list).

## Verify commands

- `python3 -m json.tool .cursor/mcp.json`
- `git diff --check`

## Approval

- required: no

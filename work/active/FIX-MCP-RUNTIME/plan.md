# Plan — FIX-MCP-RUNTIME

**task_id:** FIX-MCP-RUNTIME  
**title:** Fix Kafka and PostgreSQL MCP runtime  
**lane:** FAST  
**source:** operational MCP repair (ABI mismatch + missing postgres env)

## Goal

Make `kafka-debina` and `postgres-debina` run on Node 24.18.0 (ABI 137) with pinned packages, isolated caches, and a read-only PostgreSQL role. Leave Context7 unchanged. No production/domain/ADR/skill/hook changes. No Git write.

## Allowed paths

- `.cursor/mcp.json`
- `tools/mcp/**`
- `docs/governance/**`
- `work/active/FIX-MCP-RUNTIME/**`
- `work/QUEUE.md`
- `HANDOFF.md`

## Steps

1. Diagnose Node 24/ABI 137 and back up existing MCP wrappers/config.
2. Pin `@confluentinc/mcp-confluent@1.5.0` and `@bytebase/dbhub@0.24.0`; rewrite wrappers with Node 24 PATH and isolated caches.
3. Create `~/.config/debina/mcp-confluent.yaml` for local PLAINTEXT Kafka.
4. Start local postgres/kafka if needed; bootstrap `debina_mcp_ro` + `postgres-mcp.env` + `dbhub.toml` outside repo.
5. Point `.cursor/mcp.json` kafka/postgres entries at the wrappers; leave context7 untouched.
6. Validate wrappers, role attrs, `cursor-agent mcp list-tools`, security modes; update harness MCP docs.

## Verify commands

- `python3 -m json.tool .cursor/mcp.json`
- `bash -n tools/mcp/start-kafka-mcp.sh`
- `bash -n tools/mcp/start-dbhub.sh`
- `timeout 60s bash tools/mcp/start-kafka-mcp.sh`
- `timeout 60s bash tools/mcp/start-dbhub.sh`
- `cursor-agent mcp list` / `list-tools kafka-debina` / `list-tools postgres-debina`
- `./tools/agent/verify-fast`
- `git diff --check`

## Approval

- required: no
- decision approval path: n/a
- implementation approval path: n/a

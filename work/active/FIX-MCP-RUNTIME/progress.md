# Progress — FIX-MCP-RUNTIME

## Status

DONE (local MCP runtime repair)

## Done

- Pinned wrappers to Node `/home/suso/.nvm/versions/node/v24.18.0` (ABI 137).
- Kafka: `@confluentinc/mcp-confluent@1.5.0` with isolated cache; PATH forces Node 24 for children.
- PostgreSQL: `@bytebase/dbhub@0.24.0`; role `debina_mcp_ro` + `postgres-mcp.env` / `dbhub.toml` outside repo.
- `cursor-agent mcp list`: kafka-debina ready, postgres-debina ready; list-tools OK.
- Docs: MCP section in `CURSOR-LEAN-HARNESS-MANUAL-SETUP.md` updated.
- Context7 mcp.json entry unchanged.

## Verify evidence

- Wrapper stderr: Node v24.18.0, ABI 137; no ABI 127 / ERR_DLOPEN_FAILED.
- Role: `transaction_read_only=on`, dangerous attrs false.
- Modes: `~/.config/debina` 700; env/toml/yaml 600.

## Notes

- IDE: **Developer: Reload Window** still required for in-IDE MCP panel.
- No commit/push.

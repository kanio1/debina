# HANDOFF

## Current objective

Kafka and PostgreSQL MCP runtime repair is complete. Resume prior product work:
UCS-SCT-002-C remains ready for one coherent local commit after optional Cursor
window reload for MCP.

## Current use case

none (MCP runtime repair); prior slice UC-SCT-002 / UCS-SCT-002-C still uncommitted

## Current state

```yaml
workflow_phase: READY_TO_COMMIT
active_story: "none (FIX-MCP-RUNTIME done); prior E1 / UCS-SCT-002-C still pending commit"
active_use_case: "none | prior UC-SCT-002"
active_slice: "none | prior UCS-SCT-002-C"
readiness_verdict: NOT_APPLICABLE
last_completed_step: "FIX-MCP-RUNTIME — kafka-debina and postgres-debina ready on Node 24 ABI 137"
last_verification: "cursor-agent mcp list (kafka/postgres ready); list-tools OK; ./tools/agent/verify-fast PASS"
working_tree: modified
next_action: "Developer: Reload Window for in-IDE MCP, then create the coherent UCS-SCT-002-C commit when ready"
```

- No `work/ACTIVE.json`. Queue `NOW` empty; `FIX-MCP-RUNTIME` in `RECENTLY_COMPLETED`.
- User MCP secrets under `~/.config/debina/` (modes 700/600); caches under `~/.cache/debina-mcp/`.
- Context7 mcp.json entry unchanged. CLI may still show context7/next-devtools as needing approval.
- Backup: `/tmp/debina-mcp-repair-backup-20260728121029/`

## Completed

- Lean Cursor harness (archived) plus MCP runtime fix: Node 24.18.0 / ABI 137 wrappers,
  pinned `@confluentinc/mcp-confluent@1.5.0` and `@bytebase/dbhub@0.24.0`, isolated caches,
  read-only role `debina_mcp_ro`, docs MCP section updated.
- Prior UCS-SCT-002-C implementation remains in tree awaiting human commit.

## Decisions

- Three lanes: FAST / STANDARD / DECISION; `planning/` stays canonical backlog; `work/` is operational overlay.
- Kafka/Postgres MCP must run under absolute Node 24 path with ABI 137; never Cursor’s bundled Node for native Kafka addon.
- Secrets for MCP stay outside the repo (`~/.config/debina/`); never in `.cursor/mcp.json`.

## Blocked for production

- Full EPC TVS / scheme certification for IBAN, EUR-only and UETR rules
- Per-country IBAN length registry and BIC-only account paths
- `csm.response.received` / status-reason FSM handoff (blocks EPIC-20 Story 20.3)
- UC-SCT-003 file-rail partial-processing semantics (blocks EPIC-73 Story 73.3+)
- Playwright first-three-screen gate for EPIC-76 Story 76.7
- Dated specialist approvals / review councils / canonical migration admission
- Remote CI provider selection, GitHub Actions, `act`, deployment/release automation
- Dagger Cloud / persistent smoke-state volumes (forbidden)
- Serena MCP not configured in this environment (manual if desired)

## Next tasks

1. **Owner: human** — Run **Developer: Reload Window** so the IDE MCP panel picks up
   kafka/postgres wrappers; confirm Context7 still authenticated.
   **Done when:** IDE MCP shows kafka-debina and postgres-debina connected.
   **Verify:** Cursor Settings → MCP; `cursor-agent mcp list`

2. **Owner: scrum-master** — Create one coherent commit for UCS-SCT-002-C with message
   `feat(e1): harden pain001 profile validation` when the human authorises Git write.
   **Done when:** commit exists; slice files clean. **Verify:** `git status --short`

3. **Owner: human** — Optionally commit lean harness + MCP wrapper/docs as a separate
   config commit (no GitHub push unless asked).
   **Done when:** harness/MCP paths committed or explicitly deferred.
   **Verify:** `git status --short`

4. **Owner: enterprise-use-case-engineering** — After E1 commit, advance capability queue
   to EPIC-76.3–76.4.
   **Done when:** HANDOFF names next active slice.
   **Verify:** `python3 tools/agent-config/validate-handoff.py`

## Resume from here

Developer: Reload Window for in-IDE MCP, then create the coherent UCS-SCT-002-C commit when ready

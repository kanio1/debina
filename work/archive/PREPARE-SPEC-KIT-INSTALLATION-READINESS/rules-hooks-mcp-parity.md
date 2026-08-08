# Rules / Hooks / MCP Parity

## Shared model (target)

```text
Skills  → same canonical Debina content; Spec Kit skills platform-local
Hooks   → shared safety semantics; different adapters
Rules   → Cursor routing ≠ Codex command policy
MCP     → shared wrappers; different manifests
```

---

## Skills

| Concern | Status |
| --- | --- |
| Canonical content | `.claude/skills` |
| Cursor visibility | whole symlink `.cursor/skills` |
| Codex visibility | whole symlink `.agents/skills` (+ `tools/codex/.agents/skills`) |
| Parity of Debina skill text | Identical (single physical tree) |
| Risk | Spec Kit install through whole symlink |

---

## Hooks

### Cursor (verified local)

| Hook | Event | failClosed | Role |
| --- | --- | --- | --- |
| `command-guard.py` | `beforeShellExecution` | true | Deny git write/destructive shell |
| `scope-approval-gate.py` | `preToolUse` Write/Edit/Delete | true | ACTIVE lane, allowed_paths, approvals |
| `checkpoint.py` | `preCompact`, `stop` | false | Remind progress/HANDOFF |
| `health-check.py` | not in hooks.json | n/a | Synthetic tests via `tools/agent/health-check` |

Bootstrap behavior (scope gate):

- No `ACTIVE.json` → only `work/**` writes allowed.
- FAST → requires `plan.md` + `verify_commands`.
- STANDARD → requires `work/approvals/<TASK>.approved`.
- DECISION → requires decision + implementation approval files.
- Enforces `allowed_paths` (+ overlay for ACTIVE/QUEUE/HANDOFF/task dir).

Shell write bypass status:

- Write/Edit tools are gated by scope-approval-gate.
- Shell is gated by command-guard (dangerous patterns denied).
- Shell can still write workspace files if not matching deny patterns (**Shell write bypass exists** relative to Write-tool scope gate). Documented risk; future shared `tools/agent-policy` should close or explicitly allowlist work-path writers.

### Codex

| Item | Status |
| --- | --- |
| Repo `.codex/hooks/**` | Absent |
| Codex CLI hook support | Mentioned in CLI flags (`--dangerously-bypass-hook-trust`) → hooks exist as product feature |
| Local Debina Codex hook adapter | Not present |
| `CODEX_HOOK_PARITY` | **`UNVERIFIED`** |

Target:

```text
tools/agent-policy/**     → shared safety semantics (future)
.cursor/hooks/**          → Cursor adapter (exists)
.codex/hooks/**           → Codex adapter only if locally proven
```

Hooks must **not** drive Spec Kit workflow, select FAST/STANDARD/DECISION profiles, generate specs, pick next tasks, or implement Design Council.

Hook safety status: **Cursor fail-closed for shell danger + write scope = healthy**; Codex parity unknown.

---

## Rules

### Cursor Rules (`.cursor/rules/*.mdc`)

| File | Classification |
| --- | --- |
| `00-core.mdc` | Mix: `CURSOR_ROUTING` + portable reminders duplicated from AGENTS (`PORTABLE_GOVERNANCE` / `DUPLICATE` risk) |
| `05-terminal.mdc` | `CURSOR_SAFETY` |
| `10-documentation.mdc` | `CURSOR_ROUTING` + portable doc norms |
| `20-backend.mdc` | `CURSOR_ROUTING` |
| `25-frontend.mdc` | `CURSOR_ROUTING` |
| `30-database.mdc` | `CURSOR_ROUTING` |
| `40-testing.mdc` | `CURSOR_ROUTING` + lane proof intensity |

Target: keep Cursor rules thin (globs, skill pointers). Move durable policy to AGENTS/Skills.

### Codex Rules

| Item | Status |
| --- | --- |
| Repo `.codex/rules/**` | Absent |
| Intended role | `CODEX_COMMAND_POLICY` (allow/prompt/forbidden, sandbox) |
| Must not be | Copy of Cursor MDC |

### Portable policy only visible in Cursor today (risk)

These appear strongly in Cursor MDC / hooks and may be weaker for Codex unless also in AGENTS/Skills:

| Topic | Cursor location | Portable home |
| --- | --- | --- |
| Write scope vs ACTIVE allowed_paths | `scope-approval-gate.py` | Should be documented in AGENTS + future shared policy |
| Shell deny patterns | `command-guard.py` + `cli.json` | AGENTS already forbids git writes; shell details Cursor-specific |
| Terminal allowlist prefixes | `permissions.json` | Cursor-specific |
| MCP tool allowlist | `permissions.json` | Cursor-specific |

AGENTS.md already carries core lane/single-writer/ADR/MCP read-only policy — **good portable base for Codex**.

---

## MCP

### Cursor (`.cursor/mcp.json`)

| Server | Transport | Notes |
| --- | --- | --- |
| `context7` | remote OAuth URL | Separate adapter; no API key in repo |
| `next-devtools` | `npx -y next-devtools-mcp@latest` | Version float via `@latest` (known tension with pin policy) |
| `postgres-debina` | `tools/mcp/start-dbhub.sh` | Node 24.18.0 / ABI 137 enforced |
| `kafka-debina` | `tools/mcp/start-kafka-mcp.sh` | Node 24.18.0 / ABI 137 enforced |

Secrets: **not in repo MCP JSON**. Postgres DSN in `~/.config/debina/postgres-mcp.env`.

Read-only claims:

- Governance docs require Postgres role `debina_mcp_ro` with `default_transaction_read_only=on`.
- Kafka MCP intended local read-only connection (config outside repo).
- Wrappers themselves do not encode SQL write locks; role/config does.

### Codex MCP

| Item | Status |
| --- | --- |
| Repo MCP config | Absent |
| `~/.codex/config.toml` | No MCP servers configured (verified earlier) |
| Can reuse wrappers | Yes — same bash wrappers if Codex MCP entries point to them |

### Target ownership

```text
tools/mcp/**        → shared wrappers (Debina)
.cursor/mcp.json    → Cursor adapter
.codex/config.toml  → Codex adapter (when needed)
```

**Spec Kit must not take MCP ownership.**

MCP parity: **wrappers shared-capable; manifests Cursor-only today** → `PARTIAL`.

---

## AGENTS / CLAUDE / constitution

| File | Role |
| --- | --- |
| `AGENTS.md` + nested | Portable policy (canonical) |
| `CLAUDE.md` + nested | Compatibility; may duplicate; treat carefully |
| Future `.specify/memory/constitution.md` | Thin adapter pointing to AGENTS, ADRs, source matrix, Use-Case 2.0, architecture method, cross-stack standard, DoD, autonomy |

Mandatory:

```text
Spec Kit artifacts may not override accepted Debina governance,
ADRs, source authority or frozen invariants.
```

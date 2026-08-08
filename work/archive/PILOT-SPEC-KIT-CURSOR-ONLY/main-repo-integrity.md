# Main repo integrity

## Baseline identity

| Field | Value |
|---|---|
| branch | `rebase/enterprise-evolution` |
| HEAD | `196d40596c0049c03d20889e83e6539463381758` |
| Spec Kit in Debina | **not installed** (no `.specify/`, no `speckit-*` under Debina `.cursor/skills`) |

## Pilot-allowed Debina writes

```text
work/ACTIVE.json
work/QUEUE.md
work/active/PILOT-SPEC-KIT-CURSOR-ONLY/**
work/approvals/PILOT-SPEC-KIT-CURSOR-ONLY.approved
```

All Spec Kit filesystem operations were under `/tmp/debina-spec-kit-cursor-pilot/**`.

## End-of-pilot checks

| Check | Result |
|---|---|
| `git status --short` | Dirty tree includes **pre-existing** unrelated paths + pilot `work/` artifacts |
| `git diff --name-only` | No Spec Kit paths under Debina `.cursor/skills`, `.specify`, Codex, backend, frontend |
| `git diff --stat` | Unrelated prior task diffs remain; not restored |
| `git diff --check` (standalone) | **PASS** (exit 0) |
| `./tools/agent/task-status` | Active = `PILOT-SPEC-KIT-CURSOR-ONLY` |
| `./tools/agent/health-check` | **PASS** (synthetic 37) |
| `./tools/agent/verify-fast` | **FAIL** — pre-existing / out-of-scope (see below) |

## verify-fast failure attribution (not introduced by Spec Kit pilot)

1. `HANDOFF-010` — Resume vs `next_action` mismatch (`HANDOFF.md` is **forbidden** write path for this task)
2. `CLAUDE.md` — `git diff --check` inside verify-fast: `error: open("CLAUDE.md"): Brak dostępu` (access/ignore issue; pilot did not touch CLAUDE.md)
3. `./mvnw … compile` — local Maven repo inaccessible under sandbox (`/root/.m2`)

These failures existed as baseline risk before Spec Kit experiments (documented at bootstrap). Pilot did **not** auto-restore or reformat unrelated dirty files.

## Unrelated dirty (must remain untouched)

Examples (non-exhaustive): `.cursor/hooks/**`, `.cursor/cli.json`, `tools/agent_policy/**`, Wave 11 planning files, archived prior tasks, `FIX-CURSOR-HOOK-PHASED-APPROVAL` artifacts, `HANDOFF.md`.

## Integrity verdict

```text
MAIN_REPO_SPEC_KIT_UNTOUCHED
PILOT_WRITES_LIMITED_TO_WORK
PREEXISTING_DIRTY_PRESERVED
HEALTH_CHECK_PASS
VERIFY_FAST_FAIL_PREEXISTING
GIT_DIFF_CHECK_STANDALONE_PASS
```

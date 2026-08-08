# Spec Kit Installation Readiness Audit

**Task:** `PREPARE-SPEC-KIT-INSTALLATION-READINESS`  
**Branch:** `rebase/enterprise-evolution`  
**HEAD:** `196d40596c0049c03d20889e83e6539463381758`  
**Date:** 2026-07-29  
**Verdict:** `READY_FOR_ISOLATED_PILOT`

## Scope

Audit only. Spec Kit was **not** installed. Harness, planning, production code, Dagger, MCP wrappers, Skills, Rules, and Hooks were **not** modified (except allowed `work/**` task state).

## Tool capability matrix

| Capability | Local evidence | Status |
| --- | --- | --- |
| Python | `Python 3.14.6` (`/usr/bin/python3`) | `VERIFIED_LOCAL` |
| uv | `uv 0.11.28` (`/usr/bin/uv`) | `VERIFIED_LOCAL` |
| specify (Spec Kit CLI) | `command -v specify` empty | `NOT_INSTALLED` |
| Cursor IDE binary | `/usr/bin/cursor` present; `--version` fails in this environment (“super user / sandbox”) | `AVAILABLE_NOT_RUNTIME_VERIFIED` |
| Cursor Agent CLI | `2026.07.23-e383d2b` (`cursor-agent`) | `VERIFIED_LOCAL` |
| Codex CLI | `codex-cli 0.145.0` | `VERIFIED_LOCAL` |
| Spec Kit Cursor integration behavior | Official docs/Context7 (`/github/spec-kit`): writes `.cursor/skills/speckit-*`; `multi_install_safe=True` | `VERIFIED_OFFICIAL` |
| Spec Kit Codex integration behavior | Official docs: writes `.agents/skills`; skills mode default | `VERIFIED_OFFICIAL` |
| Codex project Hooks parity | No repo `.codex/hooks/**`; Codex help mentions hooks/trust flags | `UNSUPPORTED` locally for parity config; mark `CODEX_HOOK_PARITY = UNVERIFIED` |
| Repo `.codex/config.toml` | Absent (user home config exists, no secrets/MCP) | `VERIFIED_LOCAL` |

No configuration was designed from `UNKNOWN` tool facts. Cursor IDE version string remains unverified in this shell; Cursor Agent version is sufficient for CLI/skills pilot planning.

## Current harness summary

Debina already has a **custom lean workflow engine**:

1. `/debina-discover-and-specify` → ACTIVE + plan/progress + lane
2. Human approval (`work/approvals/`) for STANDARD/DECISION
3. `/debina-implement-and-verify` → single writer + verify wrappers
4. `/debina-review-and-next-work` → independent review + QUEUE next
5. Optional `/debina-decision-record` for DECISION

Supporting state: `work/ACTIVE.json`, `work/QUEUE.md`, `work/active/**`, `work/archive/**`, templates, hooks (`command-guard`, `scope-approval-gate`, `checkpoint`), Cursor rules, AGENTS.md tree, 33 Debina Skills, Dagger gates, MCP wrappers.

**Missing simplified UX:** no `/debina-start` or `/debina-review-next` orchestrators yet (target design only).

## Critical collision findings

1. **Whole-directory skill bridges**
   - `.agents/skills` → `../.claude/skills`
   - `.cursor/skills` → `../.claude/skills`
   - `.codex/skills` → `../.claude/skills`
   - `tools/codex/.agents/skills` → `../../../.claude/skills`
2. Spec Kit Cursor install targets `.cursor/skills`; Codex targets `.agents/skills`.
3. Installing Spec Kit into the main repo **today** would likely write `speckit-*` into the Debina canonical tree (or break/replace the symlink). **Pilot must be isolated.**
4. No broken skill symlinks found.
5. Registry: 33 ACTIVE match disk; 3 PLANNED paths absent (not blockers).

## What Spec Kit should own (target)

Generic: specification, clarification, planning, task decomposition, workflow state, pause/resume, generic human-gate mechanics, generic convergence, generic implement loop, feature artifact layout under `.specify/` + `specs/`.

## What Debina must retain

Payment BA, source authority, FAST/STANDARD/DECISION as **policy profiles**, cross-stack impact, autonomy/safety, Dagger verification profiles, independent review criteria / DoD, next-task selection against `planning/` + QUEUE, MCP read-only wrappers, domain Skills.

## What stays platform adapters

| Platform | Adapter role |
| --- | --- |
| Cursor | Rules (routing), Hooks, permissions/cli overlay, MCP manifest, optional `speckit-*` skills under `.cursor/skills` |
| Codex | Command/sandbox policy, MCP manifest, optional `speckit-*` under `.agents/skills`, entry via `tools/codex` |

## Readiness criteria check

| Criterion | Result |
| --- | --- |
| Skills roots known | Pass — canonical `.claude/skills` |
| Symlinks recognized | Pass — four bridges listed |
| `.agents/skills` collision described | Pass — whole symlink; Spec Kit write risk |
| `.cursor/skills` collision described | Pass — whole symlink; Spec Kit write risk |
| One recommended Skills model | Pass — Variant B recommended; `PILOT_REQUIRED` for Spec Kit install behavior |
| Rules/Hooks boundaries clear | Pass |
| MCP ownership clear | Pass — Spec Kit must not own MCP |
| Duplication inventoried | Pass |
| FAST/STANDARD/DECISION as profiles | Pass (designed; current harness still uses lane as write-gate) |
| Pilot plan exists | Pass — `PILOT-SPEC-KIT-CURSOR-CODEX-INTEGRATION` |
| Critical UNKNOWN | None for pilot go/no-go |

## Decision

`READY_FOR_ISOLATED_PILOT`

Not ready for in-tree install. Not a reject: dual Cursor+Codex support is documented officially; Debina domain value can remain; collisions are isolatable via worktree/temp repo and Skills ownership prep.

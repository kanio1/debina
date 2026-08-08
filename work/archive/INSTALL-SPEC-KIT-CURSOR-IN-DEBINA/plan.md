# INSTALL-SPEC-KIT-CURSOR-IN-DEBINA — Plan

## Classification

- policy_profile: STANDARD
- phase: IMPLEMENTING
- approval_state: APPROVED (user command = approval for Spec Kit v0.14.3 cursor-agent install in scoped paths)
- write_scope: IMPLEMENTATION
- lane: STANDARD (compat mirror only; not FAST)

## Goal

Install GitHub Spec Kit `v0.14.3` with `--integration cursor-agent` into the main Debina tree while preserving 33 Debina per-Skill bridges and canonical `.claude/skills`.

## Scope (allowed)

- `.specify/**`
- `.cursor/skills/speckit-*` (ten expected real directories from pilot)
- `.cursor/rules/specify-rules.mdc` (only if generated; pilot = NONE_GENERATED)
- `work/**`

## Out of scope

- Codex
- Hooks / MCP / Debina Rules
- Production code
- Feature workflow (`/speckit.specify` etc.)
- Constitution rewrite
- `.agents/skills` changes
- Commit / push

## Steps

1. Preflight + baseline (hashes, bridges, hooks/MCP)
2. Verify tag `v0.14.3` → `ec45316a…`; CLI via `uvx` reports `0.14.3`
3. Attempt `specify init --here` without `--force`
4. Conditionally `--force` only if non-empty-repo merge message and clean partial state
5. Inventory + ownership + policy review
6. Baseline-aware verify; closeout with `RUNTIME_DISCOVERY_PENDING`

## Findings to preserve

- `CURSOR_SKILLS_RUNTIME_DISCOVERY_SOURCE_AMBIGUOUS` (27/33 via `.agents/skills` presentation)

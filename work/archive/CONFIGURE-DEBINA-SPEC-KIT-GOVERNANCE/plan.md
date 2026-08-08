# CONFIGURE-DEBINA-SPEC-KIT-GOVERNANCE — Plan

## Classification

- policy_profile: STANDARD
- phase: IMPLEMENTING
- approval_state: APPROVED
- write_scope: IMPLEMENTATION
- allowed_paths: `.specify/memory/constitution.md`, `.specify/templates/**`, `work/**`

## Objective

Configure `.specify/memory/constitution.md` as a thin Debina governance adapter for Spec Kit v0.14.3, synchronizing dependent templates without duplicating canonical policy.

## Approach

1. Inventory canonical governance sources → `governance-source-map.md`
2. Draft constitution per eight required principles (user specification)
3. Apply via `speckit-constitution` skill workflow (constitution + template sync)
4. Conflict search and static flow review
5. Validation (hooks, skills layout, verify-fast, git diff --check)
6. Independent review → `constitution-review.md`
7. Closeout → `PILOT-DEBINA-SPEC-KIT-MICRO-FEATURE`

## Out of scope

- Feature workflow (`speckit-specify` and beyond)
- Extension/preset/workflow installation
- Production code, Codex, managed Spec Kit Skills
- Commit/push

## Verification

- `./tools/agent/verify-fast`
- `python3 .cursor/hooks/health-check.py`
- `python3 tools/agent-config/sync-cursor-skills.py --check`
- `git diff --check`

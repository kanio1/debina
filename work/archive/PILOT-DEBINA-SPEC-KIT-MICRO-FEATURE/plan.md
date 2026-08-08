# PILOT-DEBINA-SPEC-KIT-MICRO-FEATURE — Plan

## Classification

- policy_profile: STANDARD
- phase: ANALYZING → SPEC_READY (end of Phase A)
- approval_state: PENDING
- write_scope: WORK_ONLY

## Objective

Phase A: Spec Kit workflow (specify → clarify → plan → tasks → analyze) for Markdown Single-H1 Checker pilot. No implementation.

## Feature location

Harness blocked canonical paths during ANALYZING:

- `.specify/feature.json` (repo root) — **not writable**
- `specs/001-markdown-h1-checker/**` (repo root) — **not writable**

Feature artifacts created at:

`work/active/PILOT-DEBINA-SPEC-KIT-MICRO-FEATURE/specs/001-markdown-h1-checker/`

Pointer: `work/active/PILOT.../.specify-feature.json`

## Branch protection

- Branch unchanged: `rebase/enterprise-evolution`
- No `git switch` / `git checkout` / `git branch`
- speckit-specify branch creation: optional via extension hooks only; no `.specify/extensions.yml` — **no branch created**

## Phase B (deferred)

Requires: `work/approvals/PILOT-DEBINA-SPEC-KIT-MICRO-FEATURE.approved`, `phase=IMPLEMENTING`, `approval_state=APPROVED`

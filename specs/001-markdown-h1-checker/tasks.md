---
description: "Task list for Markdown Single-H1 Checker pilot"
---

# Tasks: Markdown Single-H1 Checker

**Input**: Design documents from `specs/001-markdown-h1-checker/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Debina task**: `PILOT-DEBINA-SPEC-KIT-MICRO-FEATURE` | **Profile**: STANDARD | **Approval**: APPROVED

## Debina Execution Constraints *(mandatory)*

- **Active task**: Implementation requires `work/ACTIVE.json` with `approval_state=APPROVED`, `phase=IMPLEMENTING`
- **Blocked**: T002–T008 until approval gate passed
- **Allowed paths (implementation)**: `tools/spec-kit-pilot/**`, `tests/tools/spec-kit-pilot/**`
- **No commit/push/PR** tasks in this list
- **Verification evidence**: Record commands, HEAD, diff fingerprint in task progress

## Phase 1: Governance Gate

**Purpose**: Confirm harness state before any implementation writes

- [x] T001 Validate `work/ACTIVE.json` shows `approval_state=APPROVED`, `phase=IMPLEMENTING`, and `allowed_paths` include `tools/spec-kit-pilot/**` and `tests/tools/spec-kit-pilot/**` before starting T002

**Checkpoint**: Human approval file `work/approvals/PILOT-DEBINA-SPEC-KIT-MICRO-FEATURE.approved` required

---

## Phase 2: Tests First (BLOCKED until T001)

**Purpose**: TDD for parsing and CLI contract

> **BLOCKED**: T002–T008 require `approval_state=APPROVED` and `phase=IMPLEMENTING`

- [x] T002 [P] Write unittest cases for `count_atx_h1` (fenced blocks, empty file, multi-H1) in `tests/tools/spec-kit-pilot/test_markdown_h1_check.py`
- [x] T003 [P] Write unittest cases for CLI exit codes and output format in `tests/tools/spec-kit-pilot/test_markdown_h1_check.py`

---

## Phase 3: Implementation (BLOCKED until T001)

- [x] T004 Implement `count_atx_h1(text: str) -> int` with fence-aware scanner in `tools/spec-kit-pilot/markdown_h1_check.py`
- [x] T005 Implement `analyze_file(path)` with UTF-8 decode and directory/missing-file ERROR handling in `tools/spec-kit-pilot/markdown_h1_check.py`
- [x] T006 Implement `main()` CLI: argument order, deterministic stdout, exit code aggregation in `tools/spec-kit-pilot/markdown_h1_check.py`

---

## Phase 4: Verification (BLOCKED until T001)

- [x] T007 Run `python3 -m unittest tests/tools/spec-kit-pilot/test_markdown_h1_check.py` and `python3 -m py_compile` on both source files; record evidence in `work/active/PILOT-DEBINA-SPEC-KIT-MICRO-FEATURE/progress.md`

---

## Phase 5: Review & Closeout (BLOCKED until T001)

- [x] T008 Independent Debina review of diff vs spec/plan; run `speckit-analyze` after implementation; prepare for `speckit-converge` in separate approved task if needed

---

## Dependencies & Execution Order

1. T001 (approval gate) blocks all implementation tasks
2. T002–T003 before T004–T006 (tests first)
3. T004 before T005–T006
4. T007 after T004–T006
5. T008 after T007

## Parallel Opportunities

- T002 and T003 can run in parallel after T001 passes

## Implementation Strategy

1. Obtain human approval (Phase B bootstrap)
2. Write failing tests (T002–T003)
3. Implement minimal module (T004–T006)
4. Verify and record evidence (T007)
5. Review (T008) — no auto-commit

**MVP scope**: User Story 1 (single-file validation) covered by T002–T007.

## Notes

- No Dagger, CI, commit, push, or PR tasks
- No changes outside planned allowed paths
- Promote feature dir to canonical `specs/001-markdown-h1-checker/` when harness allows

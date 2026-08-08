# Implementation Plan: Markdown Single-H1 Checker

**Branch**: `rebase/enterprise-evolution` (unchanged) | **Date**: 2026-07-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-markdown-h1-checker/spec.md`

## Summary

Read-only Python 3.14 CLI validating Markdown files have exactly one ATX H1 (`# `), ignoring fenced code blocks. Stdlib only; deterministic stdout; exit codes 0/1/2. Pilot tool under `tools/spec-kit-pilot/` — fully removable, no production impact.

## Technical Context

**Language/Version**: Python 3.14

**Primary Dependencies**: Python standard library only

**Storage**: N/A (read-only file analysis)

**Testing**: `unittest` in `tests/tools/spec-kit-pilot/test_markdown_h1_check.py`

**Target Platform**: Linux developer workstation (Debina repo)

**Project Type**: Standalone CLI utility (pilot)

**Performance Goals**: ≤100 files in <5s (pilot scale)

**Constraints**: Read-only; no network; no third-party packages; deterministic output

**Scale/Scope**: Single-repo pilot; no CI integration in this feature

## Debina Governance *(mandatory for STANDARD/DECISION)*

**Policy profile**: STANDARD

**Decision authority**: HUMAN_REVIEW

**Active task**: `PILOT-DEBINA-SPEC-KIT-MICRO-FEATURE`

**Allowed paths (post-approval)**: `tools/spec-kit-pilot/**`, `tests/tools/spec-kit-pilot/**`

**Verification profile**: `PILOT_PYTHON_TOOL_TARGETED`

Planned commands (Phase B):

```bash
python3 -m unittest tests/tools/spec-kit-pilot/test_markdown_h1_check.py
python3 tools/spec-kit-pilot/markdown_h1_check.py <fixtures>
python3 -m py_compile tools/spec-kit-pilot/markdown_h1_check.py \
  tests/tools/spec-kit-pilot/test_markdown_h1_check.py
git diff --check
```

**Dagger**: NOT_REQUIRED — isolated non-production Python tool, no runtime infrastructure dependency.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] Authority precedence respected (Principle I)
- [x] Task state / approval / allowed_paths satisfied for Phase A (WORK_ONLY)
- [x] Phase B implementation complete (2026-08-08); unittest 20/20 PASS
- [x] Policy profile STANDARD; not downgraded
- [x] Payment invariants N/A (non-domain tool)
- [x] Cross-stack impact — no UNKNOWN
- [x] Verification evidence plan defined
- [x] Agent safety — no commit/push in tasks
- [x] Spec Kit vs Debina ownership respected

## Cross-Stack Impact Surfaces

| Surface | Status | Notes |
|---------|--------|-------|
| business | NOT_AFFECTED | Pilot tooling only; no business behaviour |
| banking-sources | NOT_AFFECTED | No external scheme semantics |
| domain-model | NOT_AFFECTED | No payment aggregates |
| spring-modulith | NOT_AFFECTED | No Java modules |
| backend | NOT_AFFECTED | No backend code |
| postgresql | NOT_AFFECTED | No database |
| kafka | NOT_AFFECTED | No messaging |
| rest | NOT_AFFECTED | No HTTP API |
| graphql | NOT_AFFECTED | No GraphQL |
| keycloak | NOT_AFFECTED | No auth |
| bff | NOT_AFFECTED | No BFF |
| nextjs | NOT_AFFECTED | No frontend |
| react | NOT_AFFECTED | No UI |
| typescript-zod | NOT_AFFECTED | No TS schemas |
| observability | NOT_AFFECTED | No metrics/logging infra change |
| infrastructure | NOT_AFFECTED | No deploy/infra |
| testing | AFFECTED | New unittest module for pilot tool |
| documentation | NOT_AFFECTED | Spec Kit artifacts only; no canonical doc changes |

## Project Structure

### Documentation (this feature)

```text
specs/001-markdown-h1-checker/
├── spec.md
├── plan.md              # this file
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/cli-output.md
└── tasks.md
```

Mirror evidence (Phase A): `work/archive/PILOT-DEBINA-SPEC-KIT-MICRO-FEATURE/specs/001-markdown-h1-checker/` after closeout.

### Source Code (implemented — Phase B)

```text
tools/spec-kit-pilot/
└── markdown_h1_check.py

tests/tools/spec-kit-pilot/
└── test_markdown_h1_check.py
```

**Structure Decision**: One small module (`markdown_h1_check.py`) with:

- `count_atx_h1(text: str) -> int` — pure text analysis with fence awareness
- `analyze_file(path: Path) -> CheckResult` — UTF-8 read + error handling
- `main(argv: list[str]) -> int` — thin CLI; aggregates exit code

No plugin system, DI, or parser framework (Ousterhout: deep module, simple interface).

## Complexity Tracking

No constitution violations requiring justification.

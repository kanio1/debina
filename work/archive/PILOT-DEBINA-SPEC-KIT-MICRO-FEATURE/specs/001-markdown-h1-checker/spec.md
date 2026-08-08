# Feature Specification: Markdown Single-H1 Checker

**Feature Branch**: `rebase/enterprise-evolution` (unchanged — no feature branch)

**Created**: 2026-07-29

**Status**: Draft — Phase A (spec/plan only; implementation blocked)

**Input**: User description: Pilot micro-feature — read-only CLI tool that validates Markdown files contain exactly one ATX H1 (`# `) heading, ignoring fenced code blocks.

## Governance Alignment *(mandatory)*

- **Policy profile**: STANDARD
- **Debina task**: `PILOT-DEBINA-SPEC-KIT-MICRO-FEATURE`
- **Decision authority**: HUMAN_REVIEW
- **Approval state**: PENDING
- **Implementation allowed**: false (Phase A — spec/plan/tasks/analyze only)
- **Active task authority**: `work/ACTIVE.json` governs implementation scope; this spec is feature metadata only
- **Planned allowed paths (post-approval)**: `tools/spec-kit-pilot/**`, `tests/tools/spec-kit-pilot/**`
- **Verification profile**: `PILOT_PYTHON_TOOL_TARGETED` (local pilot profile)
- **Canonical sources**: `AGENTS.md`, `.specify/memory/constitution.md` v1.0.0
- **Payment/domain claims**: none — non-domain, reversible pilot tool
- **Approval**: STANDARD implementation requires explicit approval before writes to planned paths

## Clarifications

### Session 2026-07-29

- Q: Is Setext-style H1 supported? → A: No — ATX `# ` only in pilot v1.
- Q: Fenced code blocks? → A: Ignored for H1 detection.
- Q: Empty file? → A: `FAIL` with `H1=0`.
- Q: Multi-file output order? → A: Preserve CLI argument order.
- Q: FAIL + ERROR together? → A: Exit code `2` (ERROR precedence).
- Q: Encoding? → A: UTF-8; decode errors → ERROR.
- Q: Directory argument? → A: ERROR per path.
- Q: No arguments? → A: Usage error, exit `2`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Validate a single Markdown file (Priority: P1)

A repository maintainer runs the checker against one Markdown file before committing documentation changes.

**Why this priority**: Core value — immediate H1 feedback for one file.

**Independent Test**: Run checker with one file path; observe `PASS` or `FAIL` and exit code.

**Acceptance Scenarios**:

1. **Given** a readable file with exactly one ATX H1 outside fenced blocks, **When** the checker runs, **Then** output is `PASS <path> H1=1` and exit code is `0`.
2. **Given** a readable file with zero or more than one ATX H1, **When** the checker runs, **Then** output is `FAIL <path> H1=<count>` and exit code is `1` (if no ERROR lines).
3. **Given** `#` lines only inside fenced code blocks, **When** the checker runs, **Then** those lines do not count toward H1.

---

### User Story 2 - Validate multiple files (Priority: P2)

A maintainer checks several Markdown files in one command.

**Why this priority**: Batch validation without repo-wide scanning.

**Independent Test**: Multiple paths; one output line per file in argument order.

**Acceptance Scenarios**:

1. **Given** all files have exactly one H1, **When** the checker runs, **Then** all `PASS` and exit `0`.
2. **Given** at least one wrong H1 count and no read errors, **When** the checker runs, **Then** exit `1`.
3. **Given** any ERROR occurred, **When** the checker runs, **Then** exit `2`.

---

### User Story 3 - Invalid inputs (Priority: P3)

User passes directory, missing file, or non-UTF-8 file.

**Acceptance Scenarios**:

1. **Given** a directory path, **Then** `ERROR <path> <reason>` and exit `2`.
2. **Given** no arguments, **Then** usage on stderr and exit `2`.
3. **Given** UTF-8 decode failure, **Then** `ERROR` line and exit `2`.

### Edge Cases

- Empty file → `FAIL <path> H1=0`.
- `##` and deeper headings are not H1.
- Setext H1 not detected.
- Symlinks to files: follow and analyze (read-only).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: CLI accepts one or more file path arguments.
- **FR-002**: Count ATX H1 as line matching `^# ` (line-start `#` + space).
- **FR-003**: Do not count `##` or deeper as H1.
- **FR-004**: Ignore lines inside fenced code blocks (` ``` ` or `~~~`).
- **FR-005**: One result line per path: `PASS`, `FAIL`, or `ERROR`.
- **FR-006**: `PASS <path> H1=1`.
- **FR-007**: `FAIL <path> H1=<count>`.
- **FR-008**: `ERROR <path> <reason>`.
- **FR-009**: Exit `0` iff all readable and each has exactly one H1.
- **FR-010**: Exit `1` iff any readable file has H1 ≠ 1 and no ERROR.
- **FR-011**: Exit `2` on any ERROR or invalid usage.
- **FR-012**: Read-only — must not modify analyzed files.
- **FR-013**: Python standard library only.
- **FR-014**: No network access.
- **FR-015**: Deterministic output.

### Execution Constraints *(Debina)*

- **EC-001**: No payment, DB, Kafka, Keycloak, REST, GraphQL, or production changes.
- **EC-002**: No public API or schema changes.
- **EC-003**: Removable in one follow-up task under `tools/spec-kit-pilot/`.
- **EC-004**: Implementation blocked until `approval_state=APPROVED` and `phase=IMPLEMENTING`.

### Key Entities

- **Markdown file**, **H1 heading**, **Check result**, **Exit status**.

## Success Criteria *(mandatory)*

- **SC-001**: Single-file validation with unambiguous PASS/FAIL.
- **SC-002**: Batch of ≤100 files completes in under 5 seconds (pilot scale).
- **SC-003**: Fenced `#` never causes false FAIL when prose has one H1.
- **SC-004**: Invalid inputs never yield exit `0`.
- **SC-005**: Tool removable without affecting production.

## Assumptions

- Python 3.14; invoke via `python3 tools/spec-kit-pilot/markdown_h1_check.py`.
- Setext H1 unsupported in pilot v1.
- No CI/repo-wide scan in pilot scope.

## Out of Scope

- Auto-fix, plugins, DI, YAML config, CI/Dagger integration, payment domain.

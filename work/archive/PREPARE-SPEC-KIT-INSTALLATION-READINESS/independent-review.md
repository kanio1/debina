# Independent review — PREPARE-SPEC-KIT-INSTALLATION-READINESS

## Verdict

`PASS_WITH_FINDINGS`

## Scope reviewed

All artifacts under `work/active/PREPARE-SPEC-KIT-INSTALLATION-READINESS/`:

- plan.md, progress.md, readiness-audit.md
- duplication-assessment.md, path-ownership-matrix.md, skills-ownership.md
- rules-hooks-mcp-parity.md, policy-profile-mapping.md
- target-architecture.md, installation-runbook.md

Plus live harness evidence: `work/ACTIVE.json` (lane=FAST + policy_profile=STANDARD), hooks, skill symlinks.

## Findings

### F-01 HIGH

STANDARD analysis currently requires a technical FAST write gate.

Evidence: ACTIVE.json set `lane=FAST` while `policy_profile=STANDARD` so scope-approval-gate would allow work writes without `.approved`. This conflates rigor with write permission.

### F-02 HIGH

Shell can bypass structured Write/Edit controls.

Evidence: command-guard allows many shell commands; python/heredoc/redirect writes to the workspace are not gated by allowed_paths or approval. Audit used Shell to write ACTIVE/QUEUE after Write was denied.

### F-03 HIGH

The whole `.cursor/skills` symlink conflicts with future Spec Kit Cursor-managed skills.

Evidence: `.cursor/skills` → `.claude/skills` (whole directory). Spec Kit Cursor install targets `.cursor/skills/speckit-*`.

### F-04 MEDIUM

Cross-stack completeness lacks mechanical enforcement.

Evidence: readiness audit marked `MISSING_DEBINA_CROSS_STACK_ENFORCEMENT`; no impact-analysis template/validator.

### F-05 INFO

Codex integration is deferred and is outside the current scope.

Evidence: next queue item should be Cursor-only pilot; Codex remain unchanged for the immediate follow-on.

## Acceptance of audit deliverables

- Readiness verdict `READY_FOR_ISOLATED_PILOT` is accepted with the above findings.
- Spec Kit was not installed; harness outside `work/` was not modified by the audit.
- Findings F-01 and F-02 become the primary driver for `FIX-CURSOR-HOOK-PHASED-APPROVAL`.

## Closeout actions

1. Archive this task directory to `work/archive/PREPARE-SPEC-KIT-INSTALLATION-READINESS/`.
2. Clear `work/ACTIVE.json`.
3. Queue NOW = `FIX-CURSOR-HOOK-PHASED-APPROVAL`; NEXT = `PILOT-SPEC-KIT-CURSOR-ONLY`.
4. Keep `IMPLEMENT-PORTABLE-DEBINA-DESIGN-COUNCIL` as `ON_HOLD_PENDING_SPEC_KIT_EVALUATION`.

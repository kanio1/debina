# PREPARE-SPEC-KIT-INSTALLATION-READINESS — Plan

## Classification

- Lane (write-gate): FAST
- Policy profile (audit rigor): STANDARD
- Status target: SPEC_READY
- Approval required for this audit: no (work-only artifacts)
- Later human gate: before isolated Spec Kit pilot / adoption
- Nature of work: readiness audit and target-architecture design only
- Business behavior change: none
- Harness / production / planning writes: none outside allowed work paths

## Objective

Analyze the current Debina agent harness and design a safe migration model where GitHub Spec Kit owns generic workflow mechanics, Debina retains payment/policy expertise, and Cursor/Codex remain platform adapters — without installing Spec Kit in this task.

## Constraints

- Do not run specify init/install/use/add, uv tool install, pip/pipx install for Spec Kit.
- Do not create .specify/, specs/, or speckit-* skills/rules.
- Do not modify AGENTS.md, Skills, Rules, Hooks, MCP, Dagger, planning, or production code.
- Allowed writes: work/ACTIVE.json, work/QUEUE.md, work/active/PREPARE-SPEC-KIT-INSTALLATION-READINESS/**

## Deliverables

1. readiness-audit.md
2. duplication-assessment.md
3. path-ownership-matrix.md
4. skills-ownership.md
5. rules-hooks-mcp-parity.md
6. policy-profile-mapping.md
7. target-architecture.md
8. installation-runbook.md
9. progress.md (this plan companion)

## Decision escalation

Stop and reclassify if audit would require modifying harness, installing Spec Kit, or claiming Codex hook parity without local proof.

## Verify

- ./tools/agent/task-status
- ./tools/agent/verify-fast
- git diff --check
- Diff limited to allowed work paths only

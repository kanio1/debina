---
name: debina-discover-and-specify
description: Explicit slash workflow to classify FAST/STANDARD/DECISION, create work/ACTIVE.json and plan/progress, and produce a use-case slice or flow spec without implementing code.
disable-model-invocation: true
---

# Discover and specify

Slash-invoked only. Do not implement production code in this skill.

## Steps

1. Read `HANDOFF.md`, root `AGENTS.md`, `work/QUEUE.md`, and the cited planning/use-case/discovery sources.
2. Classify the task as FAST, STANDARD, or DECISION per AGENTS lean workflow.
3. Create or refresh `work/ACTIVE.json` with `task_id`, `title`, `lane`, `status`, `allowed_paths`, `approval_required`, `verify_commands`.
4. Create `work/active/<TASK-ID>/plan.md` and `progress.md` (use `work/templates/`).
5. Select specialist skills from `.claude/skills/` (do not copy their content). For payment/ISO/UC work start with `enterprise-use-case-engineering` and related domain skills.
6. FAST: write a 3–6 step plan with allowed paths and verify commands.
7. STANDARD: produce a Use-Case Slice or Business Flow Spec plus acceptance examples; set `approval_required: true`.
8. DECISION: hand off to `/debina-decision-record` before implementation planning; do not invent an ADR for ordinary reversible choices.
9. Do not edit backend/frontend/infra production code, migrations, or domain tests here.
10. End with `status` `SPEC_READY` or `BLOCKED` in `ACTIVE.json` and `progress.md`. Update `work/QUEUE.md` `NOW` to this task when ready.

Ask/read-only mode preferred. Never forge `work/approvals/*`.

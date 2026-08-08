# FIX-CURSOR-HOOK-PHASED-APPROVAL — Plan

## Classification

- policy_profile: STANDARD
- phase: IMPLEMENTING
- approval_state: APPROVED (user chat command 2026-07-29)
- write_scope: IMPLEMENTATION
- lane (compat): STANDARD
- Spec Kit: not installed / not in scope

## Objective

Replace lane-as-write-gate with phased approval fields so STANDARD analysis can write `work/` without implementation approval, while STANDARD implementation still requires approval, and Shell cannot bypass Write/Edit controls.

## Planned changes

1. Document current hook decision model in `hook-current-state.md`.
2. Add `tools/agent-policy/**` shared semantics.
3. Rewrite `scope-approval-gate.py` for policy_profile/phase/approval_state/write_scope.
4. Extend `command-guard.py` with shell write-effect pre-check.
5. Add `afterShellExecution` / postToolUse Shell post-check (detect unauthorized writes; no auto-restore).
6. Add `bootstrap-task`, `approve-task`, `set-task-state`, `closeout-task` wrappers.
7. Expand health-check / synthetic tests (30 cases).
8. Update `.cursor/cli.json` and harness docs.
9. Update `work/ACTIVE.example.json` and approvals README semantics.

## Explicit deletes

- delete:none required beyond normal edits

## Out of scope

Skills, Spec Kit install, Codex, MCP, Dagger, backend/frontend/infra/planning epics, commit/push.

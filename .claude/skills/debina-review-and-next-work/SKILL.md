---
name: debina-review-and-next-work
description: Explicit slash workflow for read-only review of the active task, QUEUE hygiene, and proposing exactly one NEXT item without forging approvals or implementing fixes.
disable-model-invocation: true
---

# Review and next work

Slash-invoked only. Read-only reviewer — no implementation fixes in this role.

## Steps

1. Read `work/ACTIVE.json`, plan, progress, spec/slice, and `git diff` for allowed paths.
2. Compare specification, code, tests, and diff. Prefer Ask/read-only.
3. Report only real problems (correctness, safety, frozen-ADR breach, missing proof, scope escape). Skip style nits unless they hide risk.
4. FAST: light review (scope, targeted verify, diff hygiene).
5. STANDARD/DECISION: full independent review including approvals, proof intensity, and specialist concerns.
6. On success: propose exactly one `NEXT` tied to an existing planning/use-case/discovery item.
7. Keep at most two `LATER` entries in `work/QUEUE.md`.
8. Update `work/QUEUE.md` (`RECENTLY_COMPLETED` ≤5). Clear `NOW` when archiving the task.
9. Do not create or forge approval files.
10. Do not mark production readiness. Do not commit/push/merge.

Archive `work/active/<TASK-ID>/` to `work/archive/<TASK-ID>/` only when the human accepts the review outcome. Leave no stale `work/ACTIVE.json` after harness/config tasks complete.

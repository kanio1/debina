---
name: debina-implement-and-verify
description: Explicit slash workflow to implement the active task as the sole writer, respect lane approvals and allowed_paths, run verify wrappers, and stop at VERIFYING without marking DONE.
disable-model-invocation: true
---

# Implement and verify

Slash-invoked only. Parent agent is the sole writer unless the plan assigns disjoint files.

## Steps

1. Read `work/ACTIVE.json`. Refuse to proceed if missing or incomplete.
2. Confirm `lane` is FAST, STANDARD, or DECISION.
3. If `approval_required` or lane is STANDARD/DECISION, require the matching human file under `work/approvals/` (STANDARD: `<TASK-ID>.approved`; DECISION: decision + implementation approvals per plan).
4. Work only inside `allowed_paths` (plus `progress.md` / material `HANDOFF.md`). One writer.
5. Implement in small steps; update `progress.md` after each meaningful step.
6. Run `./tools/agent/verify-fast` during iteration and `./tools/agent/verify-task` for declared `verify_commands`.
7. Activate specialist skills as needed (`debina-runtime-proof-testing` for proof intensity).
8. Never run Git write operations (`commit`, `push`, `merge`, `rebase`, `reset`, `clean`, branch switch).
9. End with `status` `VERIFYING` or `BLOCKED`. Do not set `DONE`.
10. Hand review to `/debina-review-and-next-work`.

Do not modify `build/generated-spring-modulith/javadoc.json`.

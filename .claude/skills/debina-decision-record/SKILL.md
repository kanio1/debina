---
name: debina-decision-record
description: Explicit slash workflow to decide whether a task needs no extra document, a short decision note, an RFC, an ADR, or RFC then ADR — without approving the decision.
disable-model-invocation: true
---

# Decision record

Slash-invoked only. Never self-approve a decision. Never forge approval files.

## Decide document need

Choose exactly one outcome:

1. **No extra document** — local, reversible implementation choice already covered by frozen ADR/AGENTS.
2. **Short decision note** — educational direction with one clear choice and no lasting public contract change.
3. **RFC** — real alternatives with meaningful trade-offs; RFC alone does not bind implementation.
4. **ADR** — durable, costly-to-reverse decision (module/aggregate/public API/schema/Kafka security/money path/finality).
5. **RFC then ADR** — alternatives first; ADR only after human decision.

## Rules

- Do not create RFC/ADR merely because the task is large.
- Do not create an ADR for an ordinary reversible local implementation choice.
- Cite sources; mark gaps as `[OPEN-QUESTION]` / `[ASSUMPTION]`.
- For DECISION lane, require Use-Case Slice or quality scenario via `enterprise-use-case-engineering` / architecture skills before implementation.
- Update `work/active/<TASK-ID>/plan.md` and `progress.md` with the document path and remaining human approvals.
- Stop at `SPEC_READY` or `BLOCKED`. Do not implement.

Authority: `docs/architecture/ADR-LIFECYCLE.md`, accepted ADR-N1…N17, `docs/standards/SOURCE-AUTHORITY-MATRIX.md`.

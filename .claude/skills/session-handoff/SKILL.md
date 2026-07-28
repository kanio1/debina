---
name: session-handoff
description: Read HANDOFF.md first at session start; overwrite it at session end with the eight-section operational format when the session was material. Use when ending a material session, resuming work, or recording blockers. Do not append history or invent answers to unresolved questions.
---

# session-handoff

## At session start

If root `HANDOFF.md` exists, read it **first**, before other work. Treat
**Resume from here** as the starting action, not a suggestion to renegotiate.

## When to update HANDOFF.md

Overwrite `HANDOFF.md` only when at least one of these is true:

- code, tests, documentation, or planning changed;
- material validation ran;
- a decision was recorded;
- a blocker appeared or changed;
- the next step changed;
- a longer session is ending;
- context compaction is about to discard operational state.

Do **not** update `HANDOFF.md` after:

- an ordinary question;
- read-only exploration;
- a short discussion with no decision;
- a session that did not change project state.

## At session end (material only)

Overwrite `HANDOFF.md` completely with current operational state. It is not a
growing log. Rewrite still-relevant facts into the correct sections; drop
obsolete audit narration and duplicated procedure text.

Write for a new agent with no chat context.

Refresh after material boundaries: story/slice selected; readiness verdict;
technical plan accepted; meaningful implementation checkpoint; focused
verification; independent review; ready to commit. Do not rewrite after every
command.

## Required format — eight sections, this order

```markdown
# HANDOFF

## Current objective
One or two sentences: what Debina work is in progress now.

## Current use case
Use-case / slice IDs, or `none` for pure technical work.

## Current state
Optional compact checkpoint YAML, then factual working-tree / verification status.

## Completed
Durable completed items still relevant to the next agent.

## Decisions
Short accepted educational directions. Do not paste full decision registers.

## Blocked for production
Unresolved production/legal/regulatory topics as `BLOCKED_FOR_PRODUCTION`.
These must not block safe local educational implementation.

## Next tasks
Exactly 3 to 5 dependency-ordered immediate tasks. Never the full backlog.

## Resume from here
The single first action for the next session.
```

### Optional checkpoint YAML (start of Current state)

```yaml
workflow_phase: DISCOVERY
active_story: "EPIC-XX / Story XX.Y | none"
active_use_case: "UC-... | none"
active_slice: "UCS-... | none"
readiness_verdict: "READY | BLOCKED | HUMAN_REVIEW_REQUIRED | NOT_APPLICABLE"
last_completed_step: "..."
last_verification: "command/result | none"
working_tree: "clean | modified | staged"
next_action: "one executable action"
```

Allowed phases only: `DISCOVERY`, `READINESS_BLOCKED`, `READY_TO_PLAN`,
`PLANNED`, `IMPLEMENTING`, `VERIFYING`, `READY_FOR_REVIEW`, `FIXING_REVIEW`,
`READY_TO_COMMIT`.

Do not add a ninth second-level section. Put follow-ups in Current state,
Decisions, or Next tasks. `Resume from here` must match `next_action`.

Validate with `python3 tools/agent-config/validate-handoff.py`.

Also keep `work/ACTIVE.json` / `work/QUEUE.md` consistent when those files exist.

## Rules

- `HANDOFF.md` ≠ stable project constitution (`AGENTS.md` / CLAUDE). Never merge them.
- Do not claim production readiness or regulatory conformance.
- Unresolved questions go in **Blocked for production** or **Current state**;
  never invent closure to make the handoff look cleaner.
- Keep next tasks between 3 and 5; align with Scrum Master task shaping when used.

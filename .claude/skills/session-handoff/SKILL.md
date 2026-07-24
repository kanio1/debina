---
name: session-handoff
description: Read HANDOFF.md first at session start; overwrite it at session end with the eight-section operational format. Use when ending a session, resuming work, or recording blockers. Do not append history or invent answers to unresolved questions.
---

# session-handoff

## At session start

If root `HANDOFF.md` exists, read it **first**, before other work. Treat
**Resume from here** as the starting action, not a suggestion to renegotiate.

Also follow `.cursor/rules/25-handoff.mdc`.

## At session end

Overwrite `HANDOFF.md` completely with current operational state. It is not a
growing log. Rewrite still-relevant facts into the correct sections; drop
obsolete audit narration and duplicated procedure text.

Write for a new agent with no chat context. Always update HANDOFF after material
sessions, including discussion-only sessions.

## Required format — eight sections, this order

```markdown
# HANDOFF

## Current objective
One or two sentences: what Debina work is in progress now.

## Current use case
Use-case / slice IDs, or `none` for pure technical work.

## Current state
Factual status of the working tree, verification, and open educational work.

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

## Rules

- `HANDOFF.md` ≠ stable project constitution (`AGENTS.md` / CLAUDE). Never merge them.
- Do not claim production readiness or regulatory conformance.
- Unresolved questions go in **Blocked for production** or **Current state**;
  never invent closure to make the handoff look cleaner.
- Keep next tasks between 3 and 5; align with Scrum Master task shaping when used.

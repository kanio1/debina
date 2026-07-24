---
name: business-analyst
description: Own Debina business content for behaviour-changing features — actor, goal, flows, rules and acceptance criteria. Use before technical planning when business-visible behaviour changes. Must follow enterprise-use-case-engineering for canonical artifacts. Do not use for pure refactors, tooling, formatting, dependency updates or non-behaviour spikes.
---

# Business Analyst

Own **business content**. Canonical use-case artifacts, format, identifiers and
traceability are owned by `enterprise-use-case-engineering` — call or follow
that skill; do **not** duplicate it.

## When required

Required for **normal features** and **new epic / unclear direction** that
change business-visible behaviour. Not required for pure refactoring, tooling,
formatting, dependency updates, or technical spikes with no behaviour change.

## Output only

- Actor and goal
- Trigger and preconditions
- Main flow
- Alternative/error flows
- Business rules
- Acceptance criteria
- Open questions
- Out of scope
- Traceability

## Workflow

1. Classify whether behaviour changes (`NO_USE_CASE_CHANGE` if not).
2. Follow `enterprise-use-case-engineering` for create/update of the repository
   use-case artifact (IDs, flows, slices, traceability).
3. Keep the working BA note concise (sections above).
4. Do not invent EPC, ISO, legal or regulatory requirements. Use
   `[OPEN-QUESTION]`, `[ASSUMPTION]`, or `BLOCKED_FOR_PRODUCTION`.
5. After implementation, reconcile: main flow, alternatives, acceptance
   criteria. Update the use case only for consciously accepted clarifications.
   Never silently rewrite requirements to match accidental code.

## Handoff

- Receive problem framing from Product Manager when used.
- Pass accepted behavioural intent to Technical Architect.
- Pass acceptance criteria and verify intent to Scrum Master.

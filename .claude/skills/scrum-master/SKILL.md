---
name: scrum-master
description: Convert accepted Debina plans into 3–5 dependency-ordered immediate tasks with owner skill, Done when and Verify. Maintain execution continuity without Scrum ceremony. Use after a technical plan is accepted. Do not create sprints, velocity, story points, burndown or artificial deadlines.
---

# Scrum Master

Maintain execution continuity **without Scrum ceremony**.

## When used

After an accepted plan for any task level (quick, normal, spike, major).
Not a substitute for BA use-case content or Technical Architect design.

## Convert plans into 3–5 tasks

Every task must contain:

- title
- owner skill
- relevant area or files
- Done when
- Verify
- dependency when relevant

Keep **3–5** immediate tasks only. Align `HANDOFF.md` **Next tasks** with this
list.

## Do not create

- sprints
- velocity
- story points
- burndown charts
- artificial deadlines

## Flow guardrails

Default educational flow:

`classify → mini use case when required → plan → one tranche → focused tests →
one review → HANDOFF → one coherent commit`

- Prefer one implementation tranche per commit cycle.
- Ensure focused verification commands are executable.
- After code changes, ensure one independent review runs before commit.
- Record `BLOCKED_FOR_PRODUCTION` items in HANDOFF; do not invent process gates
  that block safe local educational work.

# AGENTS.md — planning

`/planning/` is the executable epic/story/task catalog derived from the project documentation — 76 epics, organized into phases in `planning/README.md`. Nothing here is invented; every epic has a `source:` field pointing at the doc/section it comes from (skill `artifact-derived-planning`).

## Status vocabulary

Two orthogonal things, do not conflate them:

- **Formal status** (the only one written into an epic/story's frontmatter): `not-started`, `in-progress`, `blocked`, `done`.
- **Readiness** (analytical, used when triaging what to pick up next, not a frontmatter field): `READY`, `DECISION-BLOCKED`, `CAPABILITY-BLOCKED`, `SOURCE-BLOCKED`, `ITERATION-BLOCKED`, `INFRASTRUCTURE-BLOCKED`, `IMPLEMENTED-BUT-UNVERIFIED`, `DOCUMENTATION-STALE`, `DONE`.

Do not mark a story `in-progress` if it has no executable next task. Do not mark a story or task `done` until its exact `verify:` command has actually passed — do not batch unverified tasks ahead of a checked-off one.

## Capability-first dependencies

`depends_on` should name the narrowest real capability that gates the work, not a whole epic, unless the whole epic genuinely gates it. See `planning/capabilities.yaml` / `planning/capability-graph.mmd` for the capability graph and `planning/BACKLOG-REDESIGN.md` for how the current graph was derived, including any splits applied to stories that mixed more than one independently verifiable deliverable (MVP+P1, two owners, feature+acceptance-automation, proof+production, decision+implementation).

A cycle in the execution graph is a defect, never resolved by epic numbering order — mark it `[CYCLE-DETECTED]` and record the decision gate that breaks it rather than silently picking one direction.

## Playwright gate

Not tied to a hardcoded iteration number. Gated by the sequencing the Playwright vision/coverage documents (`sepa-nexus-playwright-learning-vision-and-success-criteria.md`, `sepa-nexus-playwright-test-learning-business-development.md`) actually call for — today that means: not before the relevant screens/Control Room capability exist per `planning/README.md`'s EPIC-24 status. Do not gate on "this is Iteration 0" — Iteration 0 (EPIC-00..EPIC-08) is `done`; re-derive the current gate from EPIC-24's own status each session, don't assume it never moves.

## Session workflow

Start from `HANDOFF.md`, then `planning/README.md` as the index, then the first `not-started`/`in-progress` epic in dependency order. Work story by story, task by task inside the epic file; tick a checkbox only once its `verify:` genuinely passes. Update `status` in the frontmatter whenever all of an epic's/story's tasks are ticked — checkbox state and `status` must stay consistent (skill `epic-story-task-catalog`). Overwrite `HANDOFF.md` before ending the session, every time.

---
name: artifact-derived-planning
description: Use when selecting, creating, or updating Debina planning artifacts, readiness, capabilities, epics, stories, or tasks; do not use to invent architecture or override ADR/[FREEZE] decisions.
---

# Artifact-derived planning

Derive work from the repository; do not design around gaps. Source precedence is:

`accepted ADR and [FREEZE] → authoritative blueprint → accepted decision record → current HANDOFF → capability graph and readiness → EPIC/story/task → implementation → tests and runtime evidence → external reference`.

If sources conflict, identify the stale source and update it only with authority. Otherwise record `[OPEN-QUESTION]` and classify the work `SOURCE-BLOCKED` or `DECISION-BLOCKED`; never choose the convenient interpretation.

## Selection workflow

Before selecting or implementing a story: (1) read applicable instructions, current HEAD and relevant recent commits; (2) read `HANDOFF.md`, ADRs and sources; (3) inspect implementation and tests; (4) inspect the capability graph; (5) identify the next executable task and a concrete `verify:` command; (6) classify actual readiness. Formal epic status is never readiness evidence.

Use only: `READY`, `DECISION-BLOCKED`, `CAPABILITY-BLOCKED`, `SOURCE-BLOCKED`, `ITERATION-BLOCKED`, `INFRASTRUCTURE-BLOCKED`, `IMPLEMENTED-BUT-UNVERIFIED`, `DOCUMENTATION-STALE`, `DONE`.

Detect contradictions among `HANDOFF.md`, ADRs, decision packets, planning README, EPIC files, story inventory, capability graph, code, tests and latest commits. Do not fabricate participant policy, CSM behavior, events, contracts, or transaction mechanisms. Never call code present or dependencies done at epic level sufficient to mark a story done.

Every task needs an executable verification command and expected result. Preserve explicit sequencing and the narrowest real capability dependency.

## Goal-aware execution

Under `/goal`, make completion evidence-backed. A hard blocker is a valid stop; a budget-limited checkpoint is not completion. Each continuation must inspect, edit, test, or verify evidence unless reporting success, interruption, blocker, or budget limit. After approval, resume from the checkpoint rather than repeating the audit.

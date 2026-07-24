---
name: planning-semantic-integrity
description: Review Debina epics, stories, planning records, readiness and verification for status/task contradictions, blockers, verify commands, capability claims, use-case/source/quality traces, generated-count drift and enforcement level. Use before marking planning work complete; do not use to infer implementation completion from code alone.
---

# Planning Semantic Integrity

## Purpose and inputs

Read `planning/AGENTS.md`, planning README, inventory/capability artifacts, enforcement migration record, relevant use case/rules/sources, epic and evidence. Distinguish formal status, analytical readiness, implementation state, documentation state, and validation state.

## Workflow

1. Check done stories/tasks and done epics/stories; reject unresolved blocker or current `NOT RUN` language in done work.
2. Check blocked classification, in-progress executable unchecked task, and valid verify declaration/target.
3. Check capability ownership/absence claims, narrow dependencies, duplicate IDs and generated counts.
4. For PILOT/ENFORCED stories, require valid use-case slice, business-rule, quality-scenario and source traceability; preserve LEGACY gaps as warnings.
5. Correct only conclusive contradictions and run inventory/capability plus semantic validators.

## Guardrails and example

Never mark work done just because code appears to exist. Do not global-keyword-match historical prose; use scoped story markers and deterministic capability evidence. A done approval story with an unchecked audit task is an error until task evidence is checked or formal status is honestly changed.

## Handoff

Receive approved or explicitly blocked behavioral slice references from the BA, source and architecture reviews. Planning may link a slice and its proof but must not redefine its actor goal, payment semantics or architecture ownership. A material open question, `AI_DRAFT` discovery state or source block is a readiness constraint, not an invitation to mark an implementation story READY.

## Classification contract

```yaml
vocabulary: docs/governance/methodology-assurance/CLASSIFICATION-VOCABULARY.yaml
consumes: [CREATE_USE_CASE, UPDATE_USE_CASE, ADD_FLOW, ADD_SLICE, LINK_EXISTING_SLICE, QUALITY_SCENARIO_ONLY, ARCHITECTURE_REVIEW_ONLY, NO_USE_CASE_CHANGE, SOURCE_CONFIRMED, PROJECT_INTERPRETATION, PROJECT_SIMULATION, RAIL_SPECIFIC, INSUFFICIENT_EVIDENCE, CONFLICTING_SOURCES, PARTICIPANT_DOCUMENTATION_REQUIRED, VERIFY_PER_USE, SOURCE_BLOCKED, DECISION_BLOCKED, HUMAN_REVIEW_REQUIRED, CURRENT_ARCHITECTURE_SUFFICIENT, NEW_PUBLIC_PORT, NEW_READ_MODEL, NEW_INTEGRATION_CONTRACT, NEW_ADR_REQUIRED, BOUNDARY_REVIEW_REQUIRED, AGGREGATE_REVIEW_REQUIRED, QUALITY_EXPERIMENT_REQUIRED, NO_ARCHITECTURE_CHANGE]
produces: [READY, BLOCKED, SOURCE_BLOCKED, DECISION_BLOCKED, CAPABILITY_BLOCKED, HUMAN_REVIEW_REQUIRED, NO_PLANNING_CHANGE]
```

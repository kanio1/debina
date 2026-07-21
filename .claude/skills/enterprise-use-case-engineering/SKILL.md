---
name: enterprise-use-case-engineering
description: Create or materially revise Debina business processes, commands, lifecycle transitions, events, external integrations, ISO flows, rail behaviour, approval/settlement/reconciliation/case workflows, operational reads, GraphQL views, aggregate candidates, or material business rules using Use-Case 2.0 and source-backed traceability. Do not use for formatting, dependency-only, private-refactor, test-naming, generated-file, or typo-only changes.
---

# Enterprise Use-Case Engineering

## Purpose

Act as Debina's Senior Business Analyst and Use-Case 2.0 facilitator. Treat `source → rule → use case → slice → architecture → story → proof` as the delivery chain.

## Read first

Read `AGENTS.md`, `docs/requirements/USE-CASE-METHOD.md`, the templates, process catalogue, candidate catalogue, `docs/standards/SOURCE-AUTHORITY-MATRIX.md`, `SOURCE-REGISTRY.yaml`, the concept catalogue, quality scenarios, module catalogue, relevant ADRs, and the affected epic/story. Inspect implementation/tests only as evidence.

## Quick classification

| Evidence | Outcome |
|---|---|
| new external actor goal | `CREATE_USE_CASE` |
| new path to an existing goal | `ADD_FLOW`, then assess `ADD_SLICE` |
| implementation mechanism only | `NO_USE_CASE_CHANGE` |
| measurable quality requirement | `QUALITY_SCENARIO_ONLY` |
| aggregate noun without lifecycle/invariant evidence | `AGGREGATE_REVIEW_REQUIRED` |
| participant-only rail behavior | `SOURCE_BLOCKED` |
| AI draft with material question | `HUMAN_REVIEW_REQUIRED` |

## Workflow

1. Declare system of interest/boundary; identify only external actors and an actor goal. Decide `CREATE_USE_CASE`, `UPDATE_USE_CASE`, `ADD_FLOW`, `ADD_SLICE`, `LINK_EXISTING_SLICE`, `QUALITY_SCENARIO_ONLY`, `ARCHITECTURE_REVIEW_ONLY`, `SOURCE_BLOCKED`, `DECISION_BLOCKED`, `HUMAN_REVIEW_REQUIRED`, or `NO_USE_CASE_CHANGE`.
2. Identify primary/supporting actors, external systems, goal, scope, trigger, preconditions, minimal/success guarantees, main success scenario, alternatives, and failures.
3. Select OUTLINE/ESSENTIAL/FULLY_DRESSED by risk; perform Example Mapping with an honest `AI_DRAFT` review state. Record out-of-scope as Debina extension and rules with source-evidence applicability.
4. Identify state transitions, messages/events, ownership, security and quality scenarios, observability, transaction boundary, and architecture realization.
5. Create/update parent use case, flow IDs, behavioral slices, test/realization links, source gaps/open questions, and epic/story traceability. Handoff to payment modeling, architecture review, then planning integrity.
6. Classify readiness. A business story is not `READY` without a slice, goal, actor/external system, main flow, source classification, applicable rules, owner, architecture realization, and executable verify.

## Guardrails

Never invent EPC, ISO, CSM, STEP2, TIPS, RT1, STET, participant, deadline, status, or aggregate behaviour. Mark missing evidence `[OPEN-QUESTION]`, `[SOURCE-GAP]`, `[PARTICIPANT-DOCUMENTATION-REQUIRED]`, or `[PROJECT-SIMULATION]`. Do not turn a technical task into a fictitious business-actor use case; use an explicit quality/operability/security/architecture/governance scenario.

## Outputs and validation

Use `docs/requirements/use-cases/`, `USE-CASE-CATALOG.yaml`, `business-rules/`, and `BUSINESS-RULE-CATALOG.yaml`. Run the use-case, source, planning, module, and ADR validators plus the relevant existing inventory/capability checks. Do not mark work done merely because code exists.

## Example

For a new payment approval decision, link BP-03 and a stable approval slice; distinguish the project approval policy from SCT scheme behaviour; record maker/checker separation as a project rule only when implementation/ADR evidence supports it.

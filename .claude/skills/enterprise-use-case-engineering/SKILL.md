---
name: enterprise-use-case-engineering
description: Create or materially revise Debina business processes, external actor goals, use cases, flows, behavioral slices, material rules, lifecycle transitions, commands/events, payment/ISO/rail integrations, approval/settlement/reconciliation/case workflows, journey-driven operational reads or GraphQL views, aggregate candidates, and new or materially changed business epics/stories. Do not use for typo/formatting/comment-only edits, dependency or generated-file refreshes, test renames, Dagger-only work, private refactors, technical optimizations, or infrastructure work without an external business actor and goal.
---

# Enterprise Use-Case Engineering

## Purpose and boundary

Preserve:

`source → business rule → business process → use case → flow → behavioral slice → architecture realization → epic → story → executable verify → runtime proof`.

Start with Use-Case Foundation actor/goal/flows; use Use-Case 2.0 only for
behavioral slices, tests and realization. Never manufacture an actor named
system, developer, pipeline, database or module for technical work.

For non-business changes return `NO_USE_CASE_CHANGE`,
`QUALITY_SCENARIO_ONLY`, or `ARCHITECTURE_REVIEW_ONLY`. A quality-only change
does not need a fictitious actor goal.

## Read first

Read in order:

1. `AGENTS.md`, `docs/requirements/USE-CASE-METHOD.md` and the selected template.
2. Business-process/rule/use-case catalogues and the affected use case/story.
3. `SOURCE-AUTHORITY-MATRIX.md`, `SOURCE-REGISTRY.yaml`,
   `SOURCE-EVIDENCE-CATALOG.yaml` and `SOURCE-EVIDENCE-POLICY.md`.
4. Relevant ADRs, quality scenarios, `ARCHITECTURE-METHOD.md`, context map and
   module catalogue.
5. Implementation/tests only as current-behavior evidence, never external
   payment authority.

Do not assume source discovery happened earlier: record the registry and
per-claim evidence IDs inspected in this run.

## Mandatory source-discovery gate

Before drafting or materially changing behavior:

1. Classify each material rule as law, EPC scheme, ISO 20022, rail-specific,
   project policy/interpretation, or project simulation.
2. Resolve a registry entry, then separate per-claim evidence. A link or
   registry entry is discovery, not proof.
3. Check publisher, version, publication/effective date, section,
   applicability/rail, access restriction and confidence. Treat
   `VERIFY-PER-USE`, unknown dates/sections and restricted sources explicitly.
4. Detect conflicts and never copy restricted participant content.
5. Apply `source-backed-payments-modeling` to material payment semantics and
   consume one of `SOURCE_CONFIRMED`, `PROJECT_INTERPRETATION`,
   `PROJECT_SIMULATION`, `RAIL_SPECIFIC`, `INSUFFICIENT_EVIDENCE`,
   `CONFLICTING_SOURCES`, or `PARTICIPANT_DOCUMENTATION_REQUIRED`.

Return `SOURCE_BLOCKED` for insufficient material evidence,
`PARTICIPANT_DOCUMENTATION_REQUIRED` for participant-only behavior,
`PROJECT_INTERPRETATION` for an explicit project decision, or
`PROJECT_SIMULATION` for a declared synthetic laboratory choice. Never return
`READY` for a material rule backed only by registry discovery.

## Classification and admission

Choose exactly the narrowest outcome:

`CREATE_USE_CASE`, `UPDATE_USE_CASE`, `ADD_FLOW`, `ADD_SLICE`,
`LINK_EXISTING_SLICE`, `QUALITY_SCENARIO_ONLY`,
`ARCHITECTURE_REVIEW_ONLY`, `SOURCE_BLOCKED`, `DECISION_BLOCKED`,
`HUMAN_REVIEW_REQUIRED`, or `NO_USE_CASE_CHANGE`.

Create a use case only with system of interest/boundary, external primary
actor, actor goal, trigger, stakeholders, preconditions, minimal/success
guarantees, main and alternate/failure flows, rules/source applicability,
ownership, security, architecture realization, and at least one behavioral
slice or an explicit deferral reason.

Do not create a use case for a class, table, repository, endpoint, migration,
adapter, CI tool or another implementation mechanism. Link it to an existing
slice or use a quality/infrastructure/architecture scenario.

Admit a slice only when it has a stable ID, parent use case, selected flow,
observable outcome, actor/external-system interaction, rules and source
classification, owner/realization, test examples, executable verify or explicit
blocker, and epic/story trace. A layer, table, class, endpoint, message alone,
“implement backend”, and “build UI” are not behavioral slices.

## Orchestrated workflow

1. Classify the requested change and declare boundary, external actor and goal.
2. Complete the source-discovery gate and payment-modeling handoff.
3. Draft/update the parent use case, stable flows, slice(s), rules, guarantees,
   state transitions and test examples using the risk-appropriate template.
4. Record `AI_DRAFT`/`NOT_REVIEWED`; material open questions force
   `HUMAN_REVIEW_REQUIRED` or a blocked outcome.
5. Apply `architecture-evolution-review` for module/port/event/schema/aggregate
   or other boundary changes. Aggregate nouns require lifecycle/invariant
   evidence and may return `AGGREGATE_REVIEW_REQUIRED`.
6. Apply `planning-semantic-integrity`. Accept only `READY`, `BLOCKED`,
   `SOURCE_BLOCKED`, `DECISION_BLOCKED`, `CAPABILITY_BLOCKED`,
   `HUMAN_REVIEW_REQUIRED`, or `NO_PLANNING_CHANGE`.

Do not mark a story done because documents were created.

## ENFORCED readiness

A new/material business story uses `semantic_enforcement: ENFORCED`. It is not
`READY` without resolvable `use_case_id`, `slice_id`,
`business_process_id`, `actor_or_external_system`, `actor_goal`, `main_flow`,
`source_classification`, `source_evidence`, `applicable_rules`, `module_owner`,
`architecture_realization`, `security_context`, `quality_scenarios`, and
`executable_verify`. A source-blocked use case, unresolved material source gap,
`VERIFY-PER-USE` without claim evidence, placeholder verify, or unreviewed
material question blocks readiness. Legacy stories migrate gradually.

## Outputs and validation

Update only the necessary use-case/rule/process/quality/architecture/planning
artifacts. Report classification, source-gate evidence and gaps, parent
use-case/flow/slice decision, architecture outcome, planning readiness and
human-review state.

Run:

```bash
python3 tools/requirements/validate-use-case-traceability.py
python3 tools/requirements/validate-source-traceability.py
python3 tools/requirements/validate-methodology-assurance.py
python3 tools/requirements/validate-planning-semantics.py
python3 tools/architecture/validate-module-catalog.py
python3 tools/architecture/validate-adr-lifecycle.py
python3 tools/skills/validate-enterprise-use-case-evals.py
python3 tools/agent-config/generate-story-inventory.py --check
python3 tools/agent-config/validate-story-inventory.py
```

Example: rename `PaymentRepository` → `PaymentStore` returns
`NO_USE_CASE_CHANGE`. A 500 ms/1000 TPS target returns
`QUALITY_SCENARIO_ONLY`. A new rejection path normally returns `ADD_FLOW`,
then assesses whether an observable slice is needed.

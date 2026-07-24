---
name: architecture-evolution-review
description: Review how approved Debina use-case slices, quality scenarios, ports, module boundaries, schemas, RLS, transactions, events, outbox/inbox, GraphQL, REST, BFF, Keycloak, and observability fit the existing modular architecture. Use for boundary or architecture-realization changes; do not silently redesign modules or create bounded contexts.
---

# Architecture Evolution Review

## Purpose and inputs

Map approved slices/scenarios to the current architecture. Read `ARCHITECTURE-METHOD.md`, context map/relationships, module catalogue, quality scenarios, ADRs, ownership documentation, the use case, and relevant implementation evidence.

## Workflow

1. Identify context, module owner, public/query ports, schema/RLS boundary, transaction boundary, events/outbox/inbox, adapters, Keycloak/security and observability.
2. Map the slice or scenario to a source-owned realization; keep GraphQL Query-only and BFF/REST technical adapters.
3. Assess context-map, module-admission and aggregate-admission evidence before any new boundary.
4. Return `CURRENT_ARCHITECTURE_SUFFICIENT`, `NEW_PUBLIC_PORT`,
   `NEW_READ_MODEL`, `NEW_INTEGRATION_CONTRACT`, `NEW_ADR_REQUIRED`,
   `BOUNDARY_REVIEW_REQUIRED`, `AGGREGATE_REVIEW_REQUIRED`,
   `QUALITY_EXPERIMENT_REQUIRED`, or `NO_ARCHITECTURE_CHANGE`.

## Guardrails and validation

Do not create a module/context without use-case evidence, context-map review, module admission review and consequences. Do not turn GraphQL/BFF into domain ownership. Validate module catalogue and ADR lifecycle; attach a quality scenario and executable proof where applicable.

## Handoff

Receive selected behavioral flows, source classification and candidate concepts; internal modules are never actors for a Debina-system use case. Return only realization/quality/decision outcomes to `enterprise-use-case-engineering`; pass story-facing constraints to `planning-semantic-integrity`. A new aggregate noun without an invariant and lifecycle is an `AGGREGATE_REVIEW_REQUIRED` finding, not a new actor or use case.

## Example

Maker-checker approval maps command ownership to payment-lifecycle, append-only audit to evidence-audit, approval queue reads through the payment query port and Query-only GraphQL/BFF adapters; it does not create an approval module.

## Classification contract

```yaml
vocabulary: docs/governance/methodology-assurance/CLASSIFICATION-VOCABULARY.yaml
consumes: [CREATE_USE_CASE, UPDATE_USE_CASE, ADD_FLOW, ADD_SLICE, LINK_EXISTING_SLICE, QUALITY_SCENARIO_ONLY, ARCHITECTURE_REVIEW_ONLY, SOURCE_CONFIRMED, PROJECT_INTERPRETATION, PROJECT_SIMULATION, RAIL_SPECIFIC, SOURCE_BLOCKED, DECISION_BLOCKED, HUMAN_REVIEW_REQUIRED]
produces: [CURRENT_ARCHITECTURE_SUFFICIENT, NEW_PUBLIC_PORT, NEW_READ_MODEL, NEW_INTEGRATION_CONTRACT, NEW_ADR_REQUIRED, BOUNDARY_REVIEW_REQUIRED, AGGREGATE_REVIEW_REQUIRED, QUALITY_EXPERIMENT_REQUIRED, NO_ARCHITECTURE_CHANGE]
```

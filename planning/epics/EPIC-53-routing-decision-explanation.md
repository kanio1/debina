---
status: in-progress
depends_on: [EPIC-52-routing-eligibility-reachability]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ROUTE-3, line 1287), [MVP], MVP-blocker"
---

# EPIC-53 — Routing: decyzja i wyjaśnienie (EPIC-ROUTE-3)

Niemutowalny snapshot wyjaśnienia to MVP-blocker.

## Story 53.1 — Niemutowalne `route_decisions`

status: done
depends_on: []

Taski:
- [x] **Migracja `routing.route_decisions`, brak UPDATE po zapisie (granty).**
      `verify: ./mvnw -f backend test -Dtest=RouteDecisionPersistenceMigrationTest,RouteDecisionPersistenceUpgradePathTest` → `3/0/0 PASS` (2026-07-20). V48 uses the exact §4.10 outcome/basis/scope taxonomy, RLS/`app.tenant_id`, and insert/select-only grant; no payment, settlement, or ledger state is changed.

## Story 53.2 — `route_candidate_results`

status: done
depends_on: [Story 53.1]

Taski:
- [x] **Migracja `routing.route_candidate_results`.**
      `verify: ./mvnw -f backend test -Dtest=RouteDecisionPersistenceMigrationTest` → `2/0/0 PASS` (2026-07-20). Per-candidate evidence is FK-bound to the decision and RLS-visible only through its tenant-scoped parent; a mutated permissive policy exposed a cross-tenant row and failed as expected.

## Story 53.3 — Niemutowalny snapshot wyjaśnienia (MVP-blocker)

status: done
depends_on: [Story 53.1]

Taski:
- [x] **`routing.route_decision_explanations` jako niemutowalny snapshot.**
      `verify: ./mvnw -f backend test -Dtest=RouteDecisionPersistenceMigrationTest,RouteDecisionPersistenceUpgradePathTest` → `3/0/0 PASS` (2026-07-20). The source snapshot fields are immutable by grants and tenant-scoped through the decision FK; the migration does not invent a routing command or Kafka message contract.

## Story 53.4 — Wyjaśnienie trasy w GraphQL

status: not-started
depends_on: [Story 53.3]

Taski:
- [ ] **Read model GraphQL wystawiający wyjaśnienie decyzji trasy.**
      `verify: ./mvnw -f backend test -Dtest=*RouteExplanationGraphQLTest*`

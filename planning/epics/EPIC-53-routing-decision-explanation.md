---
status: not-started
depends_on: [EPIC-52-routing-eligibility-reachability]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ROUTE-3, line 1287), [MVP], MVP-blocker"
---

# EPIC-53 — Routing: decyzja i wyjaśnienie (EPIC-ROUTE-3)

Niemutowalny snapshot wyjaśnienia to MVP-blocker.

## Story 53.1 — Niemutowalne `route_decisions`

status: not-started
depends_on: []

Taski:
- [ ] **Migracja `routing.route_decisions`, brak UPDATE po zapisie (granty).**
      `verify: ./mvnw -f backend test -Dtest=*RouteDecisionsImmutableTest*`

## Story 53.2 — `route_candidate_results`

status: not-started
depends_on: [Story 53.1]

Taski:
- [ ] **Migracja `routing.route_candidate_results`.**
      `verify: psql -c "\d routing.route_candidate_results"`

## Story 53.3 — Niemutowalny snapshot wyjaśnienia (MVP-blocker)

status: not-started
depends_on: [Story 53.1]

Taski:
- [ ] **`routing.route_decision_explanations` jako niemutowalny snapshot.**
      `verify: ./mvnw -f backend test -Dtest=*RouteExplanationSnapshotImmutabilityTest*`

## Story 53.4 — Wyjaśnienie trasy w GraphQL

status: not-started
depends_on: [Story 53.3]

Taski:
- [ ] **Read model GraphQL wystawiający wyjaśnienie decyzji trasy.**
      `verify: ./mvnw -f backend test -Dtest=*RouteExplanationGraphQLTest*`

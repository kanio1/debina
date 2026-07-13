---
status: not-started
depends_on: [EPIC-61-reconciliation-mismatch-taxonomy]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-RECON-6, line 1317), [MVP]/[P1]"
---

# EPIC-62 — Reconciliation: cykl życia wyjątku i akcje operatora (EPIC-RECON-6)

## Story 62.1 — Cykl życia wyjątku + `exception_events`

status: not-started
depends_on: []

Taski:
- [ ] **FSM `reconciliation.reconciliation_exceptions` + `exception_events`.**
      `verify: ./mvnw -f backend test -Dtest=*ExceptionLifecycleTest*`

## Story 62.2 — Assign/comment/resolve (`[P1]`)

status: not-started
depends_on: [Story 62.1]

Opis: powiązane z "Assign to me" z EPIC-24 Story 24.5 (optimistic locking).

Taski:
- [ ] **Komendy assign/comment/resolve z optimistic locking (dwóch operatorów rywalizujących o ten sam wyjątek — dokładnie jeden wygrywa).**
      `verify: ./mvnw -f backend test -Dtest=*TwoOperatorsExceptionAssignmentRaceTest*`

## Story 62.3 — Action-request jako request, nie mutacja (`[P1]`)

status: not-started
depends_on: [Story 62.1]

Taski:
- [ ] **Komendy operatora modelowane jako action-request (request-only), nigdy bezpośrednia mutacja źródła.**
      `verify: ./mvnw -f backend test -Dtest=*ActionRequestIsRequestNotMutationTest*`

## Story 62.4 — Port eskalacji do case (`[P1]`)

status: not-started
depends_on: [Story 62.1, EPIC-63-reconciliation-egress-iso-case-recon]

Taski:
- [ ] **Port eskalacji wyjątku do modułu `case`.**
      `verify: ./mvnw -f backend test -Dtest=*CaseEscalationPortTest*`

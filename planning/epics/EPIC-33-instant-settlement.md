---
status: not-started
depends_on: [EPIC-32-ledger-core, EPIC-35-settlement-strategy-resolver]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-MONEY-2, line 1251), [MVP], Iteracja 2"
---

# EPIC-33 — Rozliczenie natychmiastowe (EPIC-MONEY-2)

## Story 33.1 — `GrossInstantStrategy` jedna transakcja

status: not-started
depends_on: []

Taski:
- [ ] **Zaimplementuj `GrossInstantStrategy`: reserve→post→FINAL w jednej transakcji.**
      `verify: ./mvnw -f backend test -Dtest=*GrossInstantOneTxFlowTest*`

## Story 33.2 — Ścieżka niewystarczającej płynności

status: not-started
depends_on: [Story 33.1]

Taski:
- [ ] **Test: odrzucenie przy niewystarczającej płynności, status RJCT, brak częściowego zapisu.**
      `verify: ./mvnw -f backend test -Dtest=*InsufficientLiquidityInstantTest*`

## Story 33.3 — Timer SLA + zdarzenie breach

status: not-started
depends_on: [Story 33.1]

Taski:
- [ ] **Timer SLA + event breach.**
      `verify: ./mvnw -f backend test -Dtest=*SlaBreachTimerTest*`

## Story 33.4 — Atrybuty finalności/timeout

status: not-started
depends_on: [Story 33.1, EPIC-39-settlement-finality-model]

Opis: `revocation_cutoff`, `timeout_at` ≠ odrzucenie biznesowe.

Taski:
- [ ] **Test: `timeout_at` i `revocation_cutoff` nie są tym samym co odrzucenie biznesowe.**
      `verify: ./mvnw -f backend test -Dtest=*FinalityTimeoutAttributesTest*`

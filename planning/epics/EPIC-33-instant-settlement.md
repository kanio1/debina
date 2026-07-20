---
status: in-progress
depends_on: [EPIC-32-ledger-core, EPIC-35-settlement-strategy-resolver]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-MONEY-2, line 1251), [MVP], Iteracja 2"
---

# EPIC-33 — Rozliczenie natychmiastowe (EPIC-MONEY-2)

## Story 33.1 — `GrossInstantStrategy` jedna transakcja

status: done
depends_on: []

`[DONE 2026-07-20]`: user-approved ADR-N11 freezes the dedicated executor and narrow module-owned
`SECURITY DEFINER` command functions. `GrossInstantOneTxFlowTest` proves on PostgreSQL 18/Testcontainers
one txid and backend PID across RESERVE, POST, ON_LEDGER_POST finality and payment projection; same-command
replay is duplicate-free and injected failures before/after every command boundary roll back all durable effects.

Taski:
- [x] **Zaimplementuj `GrossInstantStrategy`: reserve→post→FINAL w jednej transakcji.**
      `verify: ./mvnw -f backend test -Dtest=*GrossInstantOneTxFlowTest*` → `3/0/0 PASS` (2026-07-20).

## Story 33.2 — Ścieżka niewystarczającej płynności

status: done
depends_on: [Story 33.1]

Taski:
- [x] **Test: odrzucenie przy niewystarczającej płynności, status RJCT, brak częściowego zapisu.**
      `verify: ./mvnw -f backend test -Dtest=*GrossInstantOneTxFlowTest*` → `3/0/0 PASS` (2026-07-20;
      `insufficientLiquidityIsAtomic_rejectsBusinessStatusAndCreatesNoMoneyOrFinality`).

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

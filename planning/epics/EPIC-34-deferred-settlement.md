---
status: not-started
depends_on: [EPIC-32-ledger-core, EPIC-37-settlement-deferred-net-cycles]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-MONEY-3, line 1252), [MVP], Iteracja 4"
---

# EPIC-34 — Rozliczenie odroczone (EPIC-MONEY-3)

## Story 34.1 — FSM cyklu + blokada G6

status: not-started
depends_on: []

Opis: test wyścigu na zamknięciu cyklu.

Taski:
- [ ] **FSM cyklu rozliczeniowego z semantyką blokady G6.**
      `verify: ./mvnw -f backend test -Dtest=*CycleCloseRaceTest*`

## Story 34.2 — Netting → pozycje

status: not-started
depends_on: [Story 34.1]

Taski:
- [ ] **Netting jako jedno zapytanie SQL → pozycje.**
      `verify: ./mvnw -f backend test -Dtest=*NettingSqlTest*`

## Story 34.3 — Wpis dziennika wsadowy + fan-out statusów

status: not-started
depends_on: [Story 34.2]

Taski:
- [ ] **Wpis do dziennika wsadowy + fan-out statusów płatności po zamknięciu cyklu.**
      `verify: ./mvnw -f backend test -Dtest=*BatchJournalFanOutTest*`

## Story 34.4 — Run rekoncyliacji (REPEATABLE READ) + severity

status: not-started
depends_on: [Story 34.3, EPIC-61-reconciliation-mismatch-taxonomy]

Taski:
- [ ] **Run rekoncyliacji po zamknięciu cyklu, izolacja REPEATABLE READ, klasyfikacja mismatch severity.**
      `verify: ./mvnw -f backend test -Dtest=*CycleReconciliationRunTest*`

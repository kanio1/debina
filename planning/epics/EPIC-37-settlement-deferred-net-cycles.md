---
status: not-started
depends_on: [EPIC-35-settlement-strategy-resolver]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-SETTLE-3, line 1295), [MVP]"
---

# EPIC-37 — Settlement: netting odroczony i cykle (EPIC-SETTLE-3)

## Story 37.1 — `NetDeferredStrategy`

status: not-started
depends_on: []

Taski:
- [ ] **Zaimplementuj `NetDeferredStrategy`.**
      `verify: ./mvnw -f backend test -Dtest=*NetDeferredStrategyTest*`

## Story 37.2 — FSM cyklu (blokada G6)

status: not-started
depends_on: [Story 37.1]

Opis: współdzielone z EPIC-34 Story 34.1.

Taski:
- [ ] **FSM cyklu z blokadą G6, test wyścigu.**
      `verify: ./mvnw -f backend test -Dtest=*CycleCloseRaceTest*` (współdzielony z EPIC-34).

## Story 37.3 — Netting → pozycje

status: not-started
depends_on: [Story 37.2]

Taski:
- [ ] **Netting → `settlement_positions`.**
      `verify: ./mvnw -f backend test -Dtest=*NettingSqlTest*` (współdzielony z EPIC-34).

## Story 37.4 — `ON_CYCLE_SETTLED`

status: not-started
depends_on: [Story 37.3]

Taski:
- [ ] **Event `ON_CYCLE_SETTLED` po zamknięciu cyklu.**
      `verify: ./mvnw -f backend test -Dtest=*OnCycleSettledEventTest*`

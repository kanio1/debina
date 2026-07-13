---
status: not-started
depends_on: [EPIC-35-settlement-strategy-resolver]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-SETTLE-6, line 1298), [MVP]/[P1]"
---

# EPIC-40 — Settlement: wyniki niewystarczającej płynności (EPIC-SETTLE-6)

## Story 40.1 — Odrzucenie (`[MVP]`)

status: not-started
depends_on: []

Taski:
- [ ] **Odrzucenie jako domyślny wynik niewystarczającej płynności.**
      `verify: ./mvnw -f backend test -Dtest=*InsufficientLiquidityRejectTest*` (współdzielony z EPIC-33 Story 33.2).

## Story 40.2 — Wynik zależny od basis+mode+cutoff

status: not-started
depends_on: [Story 40.1]

Taski:
- [ ] **Wynik niewystarczającej płynności różny wg `(basis, mode, cutoff)`.**
      `verify: ./mvnw -f backend test -Dtest=*LiquidityOutcomeByBasisModeTest*`

## Story 40.3 — `settlement_queue_items` + next-cycle (`[P1]`)

status: not-started
depends_on: [Story 40.2]

Taski:
- [ ] **Kolejkowanie do następnego cyklu przez `settlement_queue_items`.**
      `verify: ./mvnw -f backend test -Dtest=*SettlementQueueNextCycleTest*`

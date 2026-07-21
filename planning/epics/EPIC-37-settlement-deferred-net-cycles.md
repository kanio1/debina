---
status: done
depends_on: [EPIC-35-settlement-strategy-resolver]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-SETTLE-3, line 1295), [MVP]"
---

# EPIC-37 — Settlement: netting odroczony i cykle (EPIC-SETTLE-3)

## Story 37.1 — `NetDeferredStrategy`

status: done
depends_on: []

Taski:
- [x] **Zaimplementuj `NetDeferredStrategy`.**
      `verify: ./mvnw -f backend test -Dtest=NetDeferredStrategyTest` → `1/0/0 PASS` (2026-07-21).

`[DONE 2026-07-21]`: `NetDeferredStrategy` declares only the source-bound
`NET_DEFERRED` + `ISOLATED_SUBACCOUNT` pair and `ON_CYCLE_SETTLED`; it never uses
a profile/CSM name and does not make money or finality effects itself.

## Story 37.2 — FSM cyklu (blokada G6)

status: done
depends_on: [Story 37.1]

Opis: współdzielone z EPIC-34 Story 34.1.

Taski:
- [x] **FSM cyklu z blokadą G6, test wyścigu.**
      `verify: ./mvnw -f backend test -Dtest=DeferredSettlementCycleIntegrationTest` → `7/0/0 PASS` (2026-07-21; wspólne z EPIC-34 Story 34.1).

`[DONE 2026-07-21]`: V51 adds settlement-owned `OPEN → CLOSING → CLOSED →
NETTED → SETTLED` cycle state and explicit command receipts. Membership locks the
cycle row before testing `OPEN`; the concurrent add/close PostgreSQL 18 proof has
one winner and no partial/duplicate item. No next cycle is auto-created.

## Story 37.3 — Netting → pozycje

status: done
depends_on: [Story 37.2]

Taski:
- [x] **Netting → `settlement_positions`.**
      `verify: ./mvnw -f backend test -Dtest=DeferredSettlementCycleIntegrationTest` → `7/0/0 PASS` (2026-07-21; wspólne z EPIC-34 Story 34.2).

`[DONE 2026-07-21]`: a locked CLOSED cycle uses one `INSERT … SELECT` aggregation
over immutable items. Replay uses the same command receipt, positions are
duplicate-free and their integer-minor-unit sum is zero; failed precondition
leaves no positions.

## Story 37.4 — `ON_CYCLE_SETTLED`

status: done
depends_on: [Story 37.3]

Taski:
- [x] **Event `ON_CYCLE_SETTLED` po zamknięciu cyklu.**
      `verify: ./mvnw -f backend test -Dtest=DeferredSettlementCycleIntegrationTest,SettlementFinalityServiceTest` → `12/0/0 PASS` (2026-07-21).

`[DONE 2026-07-21]`: the narrow in-process `DeferredCycleSettlementFinalizer`
first persists `SETTLED`, then records one immutable `ON_CYCLE_SETTLED` authority
fact per immutable cycle item through `SettlementFinalityService` and the
payment-owned port. A non-SETTLED cycle cannot establish finality; replay returns
the existing record and changed evidence fails closed. No Kafka contract is added.

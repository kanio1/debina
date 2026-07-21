---
status: blocked
depends_on: [EPIC-35-settlement-strategy-resolver]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-SETTLE-6, line 1298), [MVP]/[P1]"
---

# EPIC-40 — Settlement: wyniki niewystarczającej płynności (EPIC-SETTLE-6)

## Story 40.1 — Odrzucenie (`[MVP]`)

status: done
depends_on: []

`[DONE 2026-07-20]`: the source-backed MVP gross-instant branch rejects typed insufficient
liquidity without a reservation, journal effect, finality record or payment outbox event. This is
the shared implementation/evidence from EPIC-33 Story 33.2, reverified independently here with
the complete PostgreSQL 18 one-transaction suite. It does not infer a uniform outcome for deferred,
CGS, prefunded or cutoff-bound profiles (those remain Story 40.2/P1 work).

Taski:
- [x] **Odrzucenie jako domyślny wynik niewystarczającej płynności.**
      `verify: ./mvnw -f backend test -Dtest=GrossInstantOneTxFlowTest` → `9/0/0 PASS` (2026-07-20;
      `insufficientLiquidityIsAtomic_rejectsBusinessStatusAndCreatesNoMoneyOrFinality`, shared with EPIC-33 Story 33.2).

## Story 40.2 — Wynik zależny od basis+mode+cutoff

status: blocked
depends_on: [Story 40.1]

`[DECISION-BLOCKED 2026-07-21]`: §4.11 supplies gross MVP rejection and broad
deferred/CGS labels, not a complete `(basis, mode, cutoff/cycle)` matrix or
precheck account/result semantics. See Wave 6 packet D6-02 and D6-05.

Taski:
- [ ] **Wynik niewystarczającej płynności różny wg `(basis, mode, cutoff)`.**
      `verify: ./mvnw -f backend test -Dtest=*LiquidityOutcomeByBasisModeTest*`

## Story 40.3 — `settlement_queue_items` + next-cycle (`[P1]`)

status: blocked
depends_on: [Story 40.2]

`[CAPABILITY-BLOCKED 2026-07-21]`: queue/retry/calendar work is P1 deferred,
Story 40.2 lacks the authoritative matrix, and ADR-N13 forbids automatic cycle
creation/selection. A queue table alone would invent policy. See D6-05.

Taski:
- [ ] **Kolejkowanie do następnego cyklu przez `settlement_queue_items`.**
      `verify: ./mvnw -f backend test -Dtest=*SettlementQueueNextCycleTest*`

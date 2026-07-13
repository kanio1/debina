---
status: not-started
depends_on: [EPIC-35-settlement-strategy-resolver]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-SETTLE-7, line 1299), [P1]"
---

# EPIC-41 — Settlement: CGS i prefunded (EPIC-SETTLE-7, `[P1]`)

## Story 41.1 — `BulkCgsLikeStrategy`

status: not-started
depends_on: []

Opis: kolejkowanie do cutoff, `CANCELLED_AT_CUTOFF`.

Taski:
- [ ] **`BulkCgsLikeStrategy` z kolejkowaniem do cutoff.**
      `verify: ./mvnw -f backend test -Dtest=*BulkCgsLikeStrategyTest*`

## Story 41.2 — `PrefundedInstantStrategy`

status: not-started
depends_on: []

Opis: `PREFUNDED_RESERVE`/`TECHNICAL_ACCOUNT_LIKE`.

Taski:
- [ ] **`PrefundedInstantStrategy` z kontem technicznym.**
      `verify: ./mvnw -f backend test -Dtest=*PrefundedInstantStrategyTest*`

---
status: not-started
depends_on: [EPIC-35-settlement-strategy-resolver, EPIC-13-ledger-ownership]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-SETTLE-2, line 1294), [MVP]"
---

# EPIC-36 — Settlement: gross instant + LedgerPort (EPIC-SETTLE-2)

## Story 36.1 — `GrossInstantStrategy` reserve→post→FINAL

status: not-started
depends_on: []

Opis: powiązane z EPIC-33 Story 33.1 — ta sama implementacja, inny kąt (settlement vs money).

Taski:
- [ ] **`GrossInstantStrategy` jedna transakcja przez `LedgerPort.reserve/post/release`.**
      `verify: ./mvnw -f backend test -Dtest=*GrossInstantLedgerPortTest*`

## Story 36.2 — `settlement_liquidity_checks`

status: not-started
depends_on: [Story 36.1]

Taski:
- [ ] **Migracja + logika `settlement_liquidity_checks`.**
      `verify: psql -c "\d settlement.settlement_liquidity_checks"`

## Story 36.3 — Test: `settlement_role` bez zapisu `ledger.*`

status: not-started
depends_on: [EPIC-13-ledger-ownership/Story 13.3]

Opis: ten sam test co EPIC-13 Story 13.3 — współdzielony.

Taski:
- [ ] **Grant-test: rola `settlement` bez uprawnienia zapisu na `ledger.*`.**
      `verify: ./mvnw -f backend test -Dtest=*SettlementRoleNoLedgerGrantTest*` (współdzielony z EPIC-13).

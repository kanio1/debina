---
status: blocked
depends_on: [EPIC-35-settlement-strategy-resolver, EPIC-13-ledger-ownership]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-SETTLE-2, line 1294), [MVP]"
---

# EPIC-36 — Settlement: gross instant + LedgerPort (EPIC-SETTLE-2)

## Story 36.1 — `GrossInstantStrategy` reserve→post→FINAL

status: blocked
depends_on: []

`[DECISION-BLOCKED 2026-07-20]`: same implementation and same blocker as EPIC-33 Story 33.1.
The PostgreSQL 18 transaction-boundary proof records four committed transaction IDs for the current
reserve→post→finality→payment-projection path. See
`GROSS-INSTANT-TRANSACTION-COORDINATION-DECISION.md`; no multi-commit orchestration is to be
described as one transaction.

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

status: done
depends_on: [EPIC-13-ledger-ownership/Story 13.3]

Opis: ten sam test co EPIC-13 Story 13.3 — współdzielony.

`[DONE 2026-07-20]`: shared PostgreSQL 18/Testcontainers evidence rerun in this decision pass:
`SettlementRoleNoLedgerGrantTest` remains 6/6 PASS. This independent proof does not authorize
multi-transaction gross-instant orchestration.

Taski:
- [x] **Grant-test: rola `settlement` bez uprawnienia zapisu na `ledger.*`.**
      `verify: ./mvnw -f backend test -Dtest=*SettlementRoleNoLedgerGrantTest*` → `6/0/0 PASS` (2026-07-20; współdzielony z EPIC-13).

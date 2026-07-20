---
status: in-progress
depends_on: [EPIC-35-settlement-strategy-resolver, EPIC-13-ledger-ownership]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-SETTLE-2, line 1294), [MVP]"
---

# EPIC-36 — Settlement: gross instant + LedgerPort (EPIC-SETTLE-2)

## Story 36.1 — `GrossInstantStrategy` reserve→post→FINAL

status: done
depends_on: []

`[DONE 2026-07-20]`: ADR-N11 authorizes the coordinator. Settlement calls the public
`GrossInstantLedgerPort` command (a typed extension of `LedgerPort`) rather than direct ledger DML;
the ledger function performs RESERVE→POST in the caller transaction and settlement then records its
own ON_LEDGER_POST finality. `GrossInstantOneTxFlowTest` is the shared PostgreSQL 18 proof.

Opis: powiązane z EPIC-33 Story 33.1 — ta sama implementacja, inny kąt (settlement vs money).

Taski:
- [x] **`GrossInstantStrategy` jedna transakcja przez modułową komendę `LedgerPort` RESERVE→POST.**
      `verify: ./mvnw -f backend test -Dtest=*GrossInstantOneTxFlowTest*` → `3/0/0 PASS` (2026-07-20).

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

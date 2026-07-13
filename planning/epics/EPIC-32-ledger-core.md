---
status: not-started
depends_on: [EPIC-09-ownership-schema-grants]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-MONEY-1, line 1250), [MVP]"
---

# EPIC-32 — Ledger (EPIC-MONEY-1)

Klejnot koronny: append-only, podwójny zapis, brak RLS (ownership grants zamiast tego, §4.7).

## Story 32.1 — DDL kont + dziennika + granty

status: not-started
depends_on: []

Taski:
- [ ] **Migracja `ledger.liquidity_accounts`, `ledger.journal_entries`, `ledger.journal_lines`, `ledger.balance_snapshots`, `ledger.ledger_reversals`** — bez RLS, granty ownership.
      `verify: psql -c "\dt ledger.*"`

## Story 32.2 — Niezmienniki reserve/post/release

status: not-started
depends_on: [Story 32.1]

Opis: test współbieżności no-double-reserve.

Taski:
- [ ] **Zaimplementuj `LedgerPort.reserve/post/release` z niezmiennikami transakcyjnymi.**
      `verify: ./mvnw -f backend test -Dtest=*NoDoubleReserveConcurrencyTest*`

## Story 32.3 — Deferred constraint trigger + niemutowalność

status: not-started
depends_on: [Story 32.1]

Taski:
- [ ] **Trigger deferred constraint odrzucający niezbalansowany wpis na COMMIT + granty blokujące UPDATE/DELETE na `journal_lines`.**
      `verify: ./mvnw -f backend test -Dtest=*UnbalancedEntryAtCommitTest*`

## Story 32.4 — Przepływ reversal

status: not-started
depends_on: [Story 32.2]

Opis: reversal to osobny wpis, nigdy mutacja; nightly Σ=0 job jako drugorzędna kontrola.

Taski:
- [ ] **Zaimplementuj przepływ `ledger.ledger_reversals` jako osobny wpis + nightly job Σ=0 jako wtórna kontrola.**
      `verify: ./mvnw -f backend test -Dtest=*LedgerReversalFlowTest*`

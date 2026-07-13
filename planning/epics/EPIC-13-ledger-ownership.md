---
status: not-started
depends_on: [EPIC-09-ownership-schema-grants, EPIC-32-ledger-core]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OWN-5, line 1261); sepa-nexus-blueprint-ownership-integration.md §9 (line 349)"
---

# EPIC-13 — Ownership: ledger (EPIC-OWN-5)

Najbardziej krytyczny grant-test w całym projekcie: `settlement` fizycznie nie może pisać do `ledger.journal_*`.

## Story 13.1 — `ledger_role` jedynym writerem

status: not-started
depends_on: []

Taski:
- [ ] **Grant-test: tylko `ledger_role` pisze do `ledger.*`.**
      `verify: ./mvnw -f backend test -Dtest=*LedgerSchemaOwnershipTest*`

## Story 13.2 — `LedgerPort` jako jedyna droga zapisu

status: not-started
depends_on: [Story 13.1]

Taski:
- [ ] **Test: `settlement` woła wyłącznie `LedgerPort`, nigdy repository `ledger.*` bezpośrednio.**
      `verify: ./mvnw -f backend test -Dtest=*SettlementNoDirectLedgerWriteTest*`

## Story 13.3 — Test: `settlement` bez grantu `ledger`

status: not-started
depends_on: [Story 13.1]

Taski:
- [ ] **SQL grant-test: rola `settlement` nie ma żadnego uprawnienia zapisu na `ledger.*`.**
      `verify: ./mvnw -f backend test -Dtest=*SettlementRoleNoLedgerGrantTest*`

## Story 13.4 — Testy deferred trigger + niemutowalność + reversal

status: not-started
depends_on: [Story 13.1, EPIC-32-ledger-core]

Opis: `journal_lines` odrzuca niezbalansowany wpis na COMMIT (deferred constraint trigger), UPDATE/DELETE zabronione, reversal to osobny wpis.

Taski:
- [ ] **Test: `journal_lines` odrzuca niezbalansowany wpis na COMMIT (deferred constraint trigger).**
      `verify: ./mvnw -f backend test -Dtest=*UnbalancedJournalEntryRejectedAtCommitTest*`
- [ ] **Test: UPDATE/DELETE na `journal_lines` odrzucone na poziomie grantów.**
      `verify: ./mvnw -f backend test -Dtest=*JournalLinesImmutabilityTest*`

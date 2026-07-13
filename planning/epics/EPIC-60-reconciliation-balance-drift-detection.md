---
status: not-started
depends_on: [EPIC-58-reconciliation-evidence-collection]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-RECON-4, line 1315), [MVP]"
---

# EPIC-60 — Reconciliation: wykrywanie dryfu salda (EPIC-RECON-4)

## Story 60.1 — Ledger vs balance-snapshot

status: not-started
depends_on: []

Taski:
- [ ] **Porównanie `ledger.journal_lines` vs `ledger.balance_snapshots`.**
      `verify: ./mvnw -f backend test -Dtest=*LedgerVsBalanceSnapshotTest*`

## Story 60.2 — `SILENT_MONEY_DRIFT`

status: not-started
depends_on: [Story 60.1]

Taski:
- [ ] **Zaimplementuj wykrywanie `SILENT_MONEY_DRIFT` jako severity `CRITICAL`.**
      `verify: ./mvnw -f backend test -Dtest=*SilentMoneyDriftTest*`

## Story 60.3 — Status vs finalność

status: not-started
depends_on: [Story 60.1, EPIC-39-settlement-finality-model]

Taski:
- [ ] **Test: rozbieżność status-vs-finalność wykrywana i eskalowana.**
      `verify: ./mvnw -f backend test -Dtest=*StatusVsFinalityMismatchTest*`

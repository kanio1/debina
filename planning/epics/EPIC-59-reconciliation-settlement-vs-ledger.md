---
status: not-started
depends_on: [EPIC-58-reconciliation-evidence-collection]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-RECON-3, line 1314), [MVP]"
---

# EPIC-59 — Reconciliation: settlement vs ledger (EPIC-RECON-3)

## Story 59.1 — Silnik porównawczy

status: not-started
depends_on: []

Taski:
- [ ] **Silnik porównujący stan `settlement` vs `ledger`.**
      `verify: ./mvnw -f backend test -Dtest=*SettlementVsLedgerComparisonTest*`

## Story 59.2 — Taksonomia mismatch: MISSING/DUPLICATE_LEDGER_POSTING/MONEY_MISMATCH/FINALITY_MISMATCH

status: not-started
depends_on: [Story 59.1]

Taski:
- [ ] **Zaimplementuj cztery typy mismatch: `MISSING`, `DUPLICATE_LEDGER_POSTING`, `MONEY_MISMATCH`, `FINALITY_MISMATCH`.**
      `verify: ./mvnw -f backend test -Dtest=*SettlementLedgerMismatchTaxonomyTest*` (fixture'y nazwane: `MISSING_LEDGER_POSTING`→`LEDGER_RISK`).

## Story 59.3 — Natychmiastowa eskalacja, brak auto-naprawy

status: not-started
depends_on: [Story 59.2]

Taski:
- [ ] **Test: wykryty mismatch eskaluje natychmiast, żaden kod nie modyfikuje źródła.**
      `verify: ./mvnw -f backend test -Dtest=*NoAutoFixEscalationTest*`

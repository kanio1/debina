---
status: not-started
depends_on: [EPIC-59-reconciliation-settlement-vs-ledger, EPIC-60-reconciliation-balance-drift-detection]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-RECON-5, line 1316), [MVP]"
---

# EPIC-61 — Reconciliation: taksonomia mismatch i severity (EPIC-RECON-5)

## Story 61.1 — Taksonomia + severity w reference-data

status: not-started
depends_on: []

Taski:
- [ ] **Katalog taksonomii mismatch + severity w `reference_data`.**
      `verify: psql -c "\d reference_data.mismatch_taxonomy"` (lub równoważny).

## Story 61.2 — Deterministyczny klasyfikator + testy polityki severity

status: not-started
depends_on: [Story 61.1]

Taski:
- [ ] **Deterministyczny klasyfikator mismatch→severity, testy polityki.**
      `verify: ./mvnw -f backend test -Dtest=*MismatchSeverityClassifierTest*`

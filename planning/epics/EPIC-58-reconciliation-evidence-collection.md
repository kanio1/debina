---
status: not-started
depends_on: [EPIC-57-reconciliation-profiles-snapshot]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-RECON-2, line 1313), [MVP]"
---

# EPIC-58 — Reconciliation: zbieranie evidence i bundle (EPIC-RECON-2)

`[FREEZE]` rekoncyliacja jest read-only detection-and-escalation, nigdy naprawą.

## Story 58.1 — Read-only `EvidenceCollector` per źródło

status: not-started
depends_on: []

Taski:
- [ ] **`EvidenceCollector` czytający `ledger`/`settlement`/`egress` wyłącznie do odczytu.**
      `verify: ./mvnw -f backend test -Dtest=*EvidenceCollectorReadOnlyTest*`

## Story 58.2 — `evidence_bundles` + `evidence_pointers`

status: not-started
depends_on: [Story 58.1]

Opis: wzorzec pointer, nie duplikacja surowych danych.

Taski:
- [ ] **Migracja `reconciliation.evidence_bundles`, `reconciliation.evidence_pointers`.**
      `verify: psql -c "\dt reconciliation.evidence_*"`

## Story 58.3 — Test: brak zapisu do źródła

status: not-started
depends_on: [Story 58.1]

Taski:
- [ ] **Grant-test: rola `reconciliation` nie ma zapisu do `ledger`/`settlement`/`egress`.**
      `verify: ./mvnw -f backend test -Dtest=*ReconciliationNoSourceWriteTest*`

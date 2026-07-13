---
status: not-started
depends_on: [EPIC-58-reconciliation-evidence-collection, EPIC-65-case-r-message-catalog]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-RECON-7, line 1318), [P1]"
---

# EPIC-63 — Reconciliation: egress/ISO/case (EPIC-RECON-7, `[P1]`)

## Story 63.1 — Rekoncyliacja artefaktu egress+potwierdzenia dostawy

status: not-started
depends_on: []

Taski:
- [ ] **Rekoncyliacja `outbound_artifacts`+`delivery_receipts`.**
      `verify: ./mvnw -f backend test -Dtest=*EgressArtifactReconciliationTest*`

## Story 63.2 — Rekoncyliacja orphan lineage ISO

status: not-started
depends_on: []

Taski:
- [ ] **Wykrywanie orphan w `iso.message_lineage`.**
      `verify: ./mvnw -f backend test -Dtest=*IsoLineageOrphanReconciliationTest*`

## Story 63.3 — Case-return vs return-payment

status: not-started
depends_on: [EPIC-66-case-reject-return-recall-rules]

Taski:
- [ ] **Test: zwrot zainicjowany przez case pasuje dokładnie do jednej płatności-zwrotu.**
      `verify: ./mvnw -f backend test -Dtest=*CaseReturnVsReturnPaymentTest*`

## Story 63.4 — Dashboardy

status: not-started
depends_on: [Story 63.1, EPIC-24-frontend-screens]

Taski:
- [ ] **Dashboard Playwright rekoncyliacji egress/ISO/case.**
      `verify: npm run test:e2e -- --grep "@smoke.*recon-egress-iso-case"`

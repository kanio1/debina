---
status: not-started
depends_on: [EPIC-57-reconciliation-profiles-snapshot]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-RECON-8, line 1319), [MVP]/[P1]"
---

# EPIC-64 — Reconciliation: obserwowalność i test lab (EPIC-RECON-8)

## Story 64.1 — Read modele

status: not-started
depends_on: []

Taski:
- [ ] **Read modele rekoncyliacji (runy, wyjątki, severity).**
      `verify: ./mvnw -f backend test -Dtest=*ReconciliationReadModelTest*`

## Story 64.2 — Dashboardy Playwright (`[P1]`)

status: not-started
depends_on: [Story 64.1, EPIC-24-frontend-screens]

Taski:
- [ ] **Dashboard Playwright rekoncyliacji.**
      `verify: npm run test:e2e -- --grep "@smoke.*reconciliation-dashboard"`

## Story 64.3 — Trasa rekoncyliacji w symulacji

status: not-started
depends_on: [Story 64.1, EPIC-17-simulation-path-enforcement]

Taski:
- [ ] **Test: symulowany scenariusz rekoncyliacji produkuje spójny ślad.**
      `verify: ./mvnw -f backend test -Dtest=*SimulationReconciliationTraceTest*`

## Story 64.4 — Duplicate-suppression/false-positive (`[P1]`)

status: not-started
depends_on: [Story 64.1]

Taski:
- [ ] **Test: duplikat wyjątku tłumiony, brak fałszywego pozytywu na powtórzonym mismatch.**
      `verify: ./mvnw -f backend test -Dtest=*DuplicateSuppressionFalsePositiveTest*`

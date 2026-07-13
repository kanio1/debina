---
status: not-started
depends_on: [EPIC-65-case-r-message-catalog]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-CASE-8, line 1282), [MVP]/[P1]"
---

# EPIC-72 — Case: obserwowalność i test lab (EPIC-CASE-8)

## Story 72.1 — Read modele

status: not-started
depends_on: []

Taski:
- [ ] **Read modele case (łańcuch R-message, powiązanie zwrotu).**
      `verify: ./mvnw -f backend test -Dtest=*CaseReadModelTest*`

## Story 72.2 — Dashboard case + rozstrzyganie komend admin (Playwright)

status: not-started
depends_on: [Story 72.1, EPIC-24-frontend-screens]

Taski:
- [ ] **Dashboard Playwright case (łańcuch R-message + link zwrotu) i rozstrzyganie przez komendy admin.**
      `verify: npm run test:e2e -- --grep "@smoke.*case-dashboard"`

## Story 72.3 — Trasa case w symulacji

status: not-started
depends_on: [Story 72.1, EPIC-17-simulation-path-enforcement]

Taski:
- [ ] **Test: symulowany scenariusz case produkuje spójny ślad.**
      `verify: ./mvnw -f backend test -Dtest=*SimulationCaseTraceTest*`

## Story 72.4 — pacs.028/pain.002 (`[P1]`)

status: not-started
depends_on: [Story 72.1]

Taski:
- [ ] **Obsługa pacs.028/pain.002 w module case.**
      `verify: ./mvnw -f backend test -Dtest=*Pacs028Pain002CaseTest*`

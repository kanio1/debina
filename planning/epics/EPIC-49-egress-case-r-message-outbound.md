---
status: not-started
depends_on: [EPIC-65-case-r-message-catalog]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-EGRESS-6, line 1308), [P1]"
---

# EPIC-49 — Egress: wychodzące case/R-message (EPIC-EGRESS-6, `[P1]`)

## Story 49.1 — Renderowanie camt.029/pacs.004 z eventów case

status: not-started
depends_on: []

Taski:
- [ ] **Renderowanie camt.029/pacs.004 z eventów modułu `case`.**
      `verify: ./mvnw -f backend test -Dtest=*CaseOutboundRenderTest*`

## Story 49.2 — Lineage artefaktu wychodzącego

status: not-started
depends_on: [Story 49.1, EPIC-29-iso-outbound-artifact-lineage]

Taski:
- [ ] **`outbound_artifact_lineage` dla artefaktów case.**
      `verify: ./mvnw -f backend test -Dtest=*CaseOutboundLineageTest*`

## Story 49.3 — Dashboard case-outbound

status: not-started
depends_on: [Story 49.1, EPIC-24-frontend-screens]

Taski:
- [ ] **Dashboard Playwright case-outbound.**
      `verify: npm run test:e2e -- --grep "@smoke.*case-outbound"`

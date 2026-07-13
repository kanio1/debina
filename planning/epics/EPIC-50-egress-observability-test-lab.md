---
status: not-started
depends_on: [EPIC-43-egress-rail-outbound-dispatch]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-EGRESS-7, line 1309), [MVP]/[P1]"
---

# EPIC-50 — Egress: obserwowalność i test lab (EPIC-EGRESS-7)

## Story 50.1 — Read modele

status: not-started
depends_on: []

Taski:
- [ ] **Read modele egress (dashboard delivery, retry, DLQ).**
      `verify: ./mvnw -f backend test -Dtest=*EgressReadModelTest*`

## Story 50.2 — Komenda `manual_delivery_actions` (resend)

status: not-started
depends_on: [Story 50.1]

Opis: bramkowana rolą, audytowana, nie przez GraphQL (komendy zawsze REST/gRPC).

Taski:
- [ ] **Komenda resend jako `manual_delivery_actions`, role-gated, audytowana, REST nie GraphQL.**
      `verify: ./mvnw -f backend test -Dtest=*ManualResendCommandTest*`

## Story 50.3 — Trasa awarii egress w symulacji

status: not-started
depends_on: [Story 50.1, EPIC-17-simulation-path-enforcement]

Taski:
- [ ] **Test: symulowana awaria egress produkuje spójny ślad w read modelach.**
      `verify: ./mvnw -f backend test -Dtest=*SimulationEgressFailureTraceTest*`

## Story 50.4 — Dashboardy nieudanej dostawy + manual-resend

status: not-started
depends_on: [Story 50.2, EPIC-24-frontend-screens]

Taski:
- [ ] **Dashboard Playwright: nieudana dostawa + manual-resend.**
      `verify: npm run test:e2e -- --grep "@smoke.*egress-failure-dashboard"`

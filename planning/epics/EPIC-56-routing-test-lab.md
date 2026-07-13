---
status: not-started
depends_on: [EPIC-53-routing-decision-explanation, EPIC-54-routing-fallback-rules]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ROUTE-6, line 1290), [MVP]"
---

# EPIC-56 — Routing: test lab (EPIC-ROUTE-6)

## Story 56.1 — Deterministyczne fixture'y routingu

status: not-started
depends_on: []

Taski:
- [ ] **Zestaw deterministycznych fixture'ów decyzji routingu.**
      `verify: ./mvnw -f backend test -Dtest=*RoutingFixturesTest*`

## Story 56.2 — Testy parami (pairwise) profili

status: not-started
depends_on: [Story 56.1]

Taski:
- [ ] **Testy pairwise kombinacji profili.**
      `verify: ./mvnw -f backend test -Dtest=*RoutingPairwiseProfileTest*`

## Story 56.3 — Testy granic kwota/waluta

status: not-started
depends_on: [Story 56.1]

Taski:
- [ ] **Testy przypadków brzegowych kwota/waluta.**
      `verify: ./mvnw -f backend test -Dtest=*RoutingBoundaryAmountCurrencyTest*`

## Story 56.4 — Dashboard routingu + scenariusze awarii w symulacji (Playwright)

status: not-started
depends_on: [Story 56.1, EPIC-17-simulation-path-enforcement, EPIC-24-frontend-screens]

Taski:
- [ ] **Dashboard Playwright routingu + scenariusze awarii routingu w symulacji.**
      `verify: npm run test:e2e -- --grep "@smoke.*routing"`

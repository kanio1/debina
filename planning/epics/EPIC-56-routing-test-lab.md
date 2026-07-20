---
status: in-progress
depends_on: [EPIC-53-routing-decision-explanation, EPIC-54-routing-fallback-rules]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ROUTE-6, line 1290), [MVP]"
---

# EPIC-56 — Routing: test lab (EPIC-ROUTE-6)

## Story 56.1 — Deterministyczne fixture'y routingu

status: done
depends_on: []

Taski:
- [x] **Zestaw deterministycznych fixture'ów decyzji routingu.**
      `verify: ./mvnw -f backend test -Dtest=RoutingFixturesTest,RouteCandidateResolutionTest,FallbackSelectedOutcomeTest` → `7/0/0 PASS` (2026-07-20). Fixed UUID/date fixtures execute the real candidate resolver and explicit fallback policy twice, proving deterministic replay instead of duplicating constants.

## Story 56.2 — Testy parami (pairwise) profili

status: done
depends_on: [Story 56.1]

Taski:
- [x] **Testy pairwise kombinacji profili.**
      `verify: ./mvnw -f backend test -Dtest=RoutingPairwiseProfileTest,FallbackSelectedOutcomeTest` → `7/0/0 PASS` (2026-07-20). Four cases cover every pair across source-profile match, null/non-null condition and input ordering. Reversing the production priority comparator produced `2` expected test failures, then was reverted and rerun green.

## Story 56.3 — Testy granic kwota/waluta

status: blocked
depends_on: [Story 56.1]

`[SOURCE-BLOCKED 2026-07-20]`: §4.10 names amount/currency checks and outcomes but defines no
amount-limit catalog, boundary values, comparison policy or currency-rule vocabulary. Exact
candidate catalog lookup cannot justify invented lower/exact/upper amount tests.

Taski:
- [ ] **Testy przypadków brzegowych kwota/waluta.**
      `verify: ./mvnw -f backend test -Dtest=*RoutingBoundaryAmountCurrencyTest*`

## Story 56.4 — Dashboard routingu + scenariusze awarii w symulacji (Playwright)

status: blocked
depends_on: [Story 56.1, EPIC-17-simulation-path-enforcement, EPIC-24-frontend-screens]

`[CAPABILITY-BLOCKED 2026-07-20]`: the required simulation-path and Control Room/frontend
capabilities are not present; this backend tranche does not create them.

Taski:
- [ ] **Dashboard Playwright routingu + scenariusze awarii routingu w symulacji.**
      `verify: npm run test:e2e -- --grep "@smoke.*routing"`

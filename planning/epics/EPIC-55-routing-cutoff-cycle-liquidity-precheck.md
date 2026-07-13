---
status: not-started
depends_on: [EPIC-53-routing-decision-explanation, EPIC-37-settlement-deferred-net-cycles]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ROUTE-5, line 1289), [MVP]/[P1]"
---

# EPIC-55 — Routing: precheck cutoff/cykl/płynność (EPIC-ROUTE-5)

## Story 55.1 — Port `CutoffStateReader`

status: not-started
depends_on: []

Opis: czyta stan settlement, nie pisze.

Taski:
- [ ] **`CutoffStateReader` jako port tylko-odczyt do `settlement`.**
      `verify: ./mvnw -f backend test -Dtest=*CutoffStateReaderPortTest*`

## Story 55.2 — Wyniki `CUTOFF_REACHED`/`CYCLE_CLOSED`

status: not-started
depends_on: [Story 55.1]

Taski:
- [ ] **Wyniki decyzji `CUTOFF_REACHED`/`CYCLE_CLOSED`.**
      `verify: ./mvnw -f backend test -Dtest=*CutoffCycleOutcomeTest*`

## Story 55.3 — `LiquidityModePrecheckPort` (`[P1]`)

status: not-started
depends_on: [Story 55.1]

Opis: gruby, tylko-odczyt.

Taski:
- [ ] **`LiquidityModePrecheckPort` — gruby, read-only.**
      `verify: ./mvnw -f backend test -Dtest=*LiquidityPrecheckPortTest*`

## Story 55.4 — Zachowanie routingu przy zamkniętym cyklu

status: not-started
depends_on: [Story 55.2]

Taski:
- [ ] **Test: routing przy `CYCLE_CLOSED` nie tworzy niejawnej decyzji.**
      `verify: ./mvnw -f backend test -Dtest=*RoutingBehaviorOnCycleClosedTest*`

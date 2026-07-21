---
status: in-progress
depends_on: [EPIC-53-routing-decision-explanation, EPIC-37-settlement-deferred-net-cycles]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ROUTE-5, line 1289), [MVP]/[P1]"
---

# EPIC-55 — Routing: precheck cutoff/cykl/płynność (EPIC-ROUTE-5)

## Story 55.1 — Port `CutoffStateReader`

status: done
depends_on: []

Opis: czyta stan settlement, nie pisze.

Taski:
- [x] **`CutoffStateReader` jako port tylko-odczyt do `settlement`.**
      `verify: ./mvnw -f backend test -Dtest=CutoffStateReaderPortTest` → `1/0/0 PASS` (2026-07-21).

`[DONE 2026-07-21]`: `JdbcCutoffStateReader` is a real settlement-role,
read-only boundary joining V9's reference-data-owned configured cutoff with V51's
settlement-owned runtime cycle. It exposes only cycle identity/state, configured
cutoff, business date/session and membership availability; it never creates or
mutates a cycle and routing gets no table grant.

## Story 55.2 — Wyniki `CUTOFF_REACHED`/`CYCLE_CLOSED`

status: blocked
depends_on: [Story 55.1]

Taski:
- [ ] **Wyniki decyzji `CUTOFF_REACHED`/`CYCLE_CLOSED`.**
      `verify: ./mvnw -f backend test -Dtest=*CutoffCycleOutcomeTest*`

`[SOURCE-BLOCKED 2026-07-21]`: §4.10 names both outcomes but does not define a
decision precedence or result for a configured passed cutoff whose runtime cycle
is still `OPEN`/otherwise disagrees. The Wave 5 boundary explicitly forbids
treating `now > cutoff_at` alone as sufficient. A source or user decision is
needed before routing behavior is implemented.

## Story 55.3 — `LiquidityModePrecheckPort` (`[P1]`)

status: blocked
depends_on: [Story 55.1]

Opis: gruby, tylko-odczyt.

Taski:
- [ ] **`LiquidityModePrecheckPort` — gruby, read-only.**
      `verify: ./mvnw -f backend test -Dtest=*LiquidityPrecheckPortTest*`

`[SOURCE-BLOCKED 2026-07-21]`: no authoritative contract identifies the exact
participant/account identity, currency/amount input, liquidity-mode result or
read-consistency boundary. It must not reproduce `LedgerPort` or infer the
blocked profile-to-liquidity mapping from Story 35.3.

## Story 55.4 — Zachowanie routingu przy zamkniętym cyklu

status: blocked
depends_on: [Story 55.2]

Taski:
- [ ] **Test: routing przy `CYCLE_CLOSED` nie tworzy niejawnej decyzji.**
      `verify: ./mvnw -f backend test -Dtest=*RoutingBehaviorOnCycleClosedTest*`

`[CAPABILITY-BLOCKED 2026-07-21]`: depends on the source-blocked outcome policy
in Story 55.2. The read boundary exists, but a routing outcome cannot be
implemented without the missing precedence/behavior authority.

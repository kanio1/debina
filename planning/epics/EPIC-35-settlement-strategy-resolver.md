---
status: not-started
depends_on: [EPIC-12-reference-data-ownership]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-SETTLE-1, line 1293), [MVP]"
---

# EPIC-35 — Settlement: resolver strategii i basis/mode (EPIC-SETTLE-1)

`[FREEZE]` strategia zawsze przez `(settlement_basis, liquidity_mode)`, nigdy po nazwie profilu/CSM.

## Story 35.1 — `SettlementStrategyResolver`

status: not-started
depends_on: []

Taski:
- [ ] **Zaimplementuj `SettlementStrategyResolver(settlement_basis, liquidity_mode)`.**
      `verify: ./mvnw -f backend test -Dtest=*SettlementStrategyResolverTest*`

## Story 35.2 — Zakaz przełączania po nazwie profilu (ArchUnit)

status: not-started
depends_on: [Story 35.1]

Taski:
- [ ] **Reguła ArchUnit: zero `switch`/`if` po nazwie profilu/CSM w kodzie wyboru strategii.**
      `verify: ./mvnw -f backend test -Dtest=*NoProfileNameSwitchTest*`

## Story 35.3 — Zamrożony snapshot profilu na attempt

status: not-started
depends_on: [Story 35.1]

Taski:
- [ ] **`settlement_profile_snapshots` zamrożony per attempt.**
      `verify: ./mvnw -f backend test -Dtest=*SettlementProfileSnapshotTest*`

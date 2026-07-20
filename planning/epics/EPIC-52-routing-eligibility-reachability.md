---
status: in-progress
depends_on: [EPIC-51-routing-candidate-profile-resolution]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ROUTE-2, line 1286), [MVP]"
---

# EPIC-52 — Routing: kwalifikowalność i osiągalność (EPIC-ROUTE-2)

## Story 52.1 — `reference_data.participant_capabilities`

status: done
depends_on: []

Taski:
- [x] **Migracja `reference_data.participant_capabilities`.**
      `verify: ./mvnw -f backend test -Dtest=RoutingEligibilityAndReachabilityMigrationTest,RoutingEligibilityAndReachabilityUpgradePathTest` → `3/0/0 PASS` (2026-07-20). V46 implements the exact §4.10 participant/profile/access-mode key; `reference_data_role` is the sole writer and `routing_role` read-only. Fresh PostgreSQL 18, V45→V47 upgrade, foreign-write denial, and grant-removal mutation proof are recorded in the Wave 3 program evidence.

## Story 52.2 — `participant_eligibility_rules`

status: done
depends_on: [Story 52.1]

Taski:
- [x] **Migracja `reference_data.participant_eligibility_rules`.**
      `verify: ./mvnw -f backend test -Dtest=RoutingEligibilityAndReachabilityMigrationTest` → `2/0/0 PASS` (2026-07-20). The generic `(profile_id, rule_code, rule_value)` shape is exactly the source DDL; no rule vocabulary or eligibility behavior is inferred.

## Story 52.3 — Osiągalność runtime per profil

status: done
depends_on: [Story 52.1]

Taski:
- [x] **`routing.participant_reachability` jako stan runtime per profil.**
      `verify: ./mvnw -f backend test -Dtest=RoutingEligibilityAndReachabilityMigrationTest,RoutingEligibilityAndReachabilityUpgradePathTest` → `3/0/0 PASS` (2026-07-20). V47 creates the routing-owned runtime table with only source enum values and its ADR-N5 outbox/inbox pair; `outbox_dispatcher_role` can mark a routing outbox row published but cannot write reachability.

## Story 52.4 — Dashboard osiągalności

status: blocked
depends_on: [Story 52.3, EPIC-24-frontend-screens]

`[CAPABILITY-BLOCKED 2026-07-20]`: the Control Room/frontend capability is not present; this
backend routing tranche does not create it.

Taski:
- [ ] **Dashboard Playwright osiągalności.**
      `verify: npm run test:e2e -- --grep "@smoke.*reachability"`

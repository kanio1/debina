---
status: not-started
depends_on: [EPIC-51-routing-candidate-profile-resolution]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ROUTE-2, line 1286), [MVP]"
---

# EPIC-52 — Routing: kwalifikowalność i osiągalność (EPIC-ROUTE-2)

## Story 52.1 — `reference_data.participant_capabilities`

status: not-started
depends_on: []

Taski:
- [ ] **Migracja `reference_data.participant_capabilities`.**
      `verify: psql -c "\d reference_data.participant_capabilities"`

## Story 52.2 — `participant_eligibility_rules`

status: not-started
depends_on: [Story 52.1]

Taski:
- [ ] **Migracja `reference_data.participant_eligibility_rules`.**
      `verify: psql -c "\d reference_data.participant_eligibility_rules"`

## Story 52.3 — Osiągalność runtime per profil

status: not-started
depends_on: [Story 52.1]

Taski:
- [ ] **`routing.participant_reachability` jako stan runtime per profil.**
      `verify: psql -c "\d routing.participant_reachability"`

## Story 52.4 — Dashboard osiągalności

status: not-started
depends_on: [Story 52.3, EPIC-24-frontend-screens]

Taski:
- [ ] **Dashboard Playwright osiągalności.**
      `verify: npm run test:e2e -- --grep "@smoke.*reachability"`

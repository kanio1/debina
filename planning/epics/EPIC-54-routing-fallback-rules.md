---
status: not-started
depends_on: [EPIC-53-routing-decision-explanation]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ROUTE-4, line 1288), [MVP]/[P1]"
---

# EPIC-54 — Routing: reguły fallback (EPIC-ROUTE-4)

## Story 54.1 — Jawne `profile_fallback_rules`

status: not-started
depends_on: []

Taski:
- [ ] **Migracja `reference_data.profile_fallback_rules`.**
      `verify: psql -c "\d reference_data.profile_fallback_rules"`

## Story 54.2 — Wynik `FALLBACK_SELECTED`

status: not-started
depends_on: [Story 54.1]

Taski:
- [ ] **Zaimplementuj wynik decyzji `FALLBACK_SELECTED`.**
      `verify: ./mvnw -f backend test -Dtest=*FallbackSelectedOutcomeTest*`

## Story 54.3 — Test: brak niejawnego fallbacku instant→batch

status: not-started
depends_on: [Story 54.2]

Taski:
- [ ] **Test: brak niejawnego fallbacku z instant do batch — musi być jawną regułą.**
      `verify: ./mvnw -f backend test -Dtest=*NoImplicitInstantToBatchFallbackTest*`

## Story 54.4 — Fallback wielo-CSM (`[P1]`)

status: not-started
depends_on: [Story 54.2]

Taski:
- [ ] **Fallback obejmujący wiele CSM.**
      `verify: ./mvnw -f backend test -Dtest=*MultiCsmFallbackTest*`

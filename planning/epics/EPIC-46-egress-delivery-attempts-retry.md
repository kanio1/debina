---
status: not-started
depends_on: [EPIC-45-egress-outbound-message-lifecycle]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-EGRESS-3, line 1305), [MVP]"
---

# EPIC-46 — Egress: próby dostawy i retry (EPIC-EGRESS-3)

## Story 46.1 — `transport_attempts`

status: not-started
depends_on: []

Taski:
- [ ] **Migracja `egress.transport_attempts`.**
      `verify: psql -c "\d egress.transport_attempts"`

## Story 46.2 — Polityka retry per profil + dead-letter

status: not-started
depends_on: [Story 46.1]

Taski:
- [ ] **Polityka retry per `egress_profile` + dead-letter po wyczerpaniu prób.**
      `verify: ./mvnw -f backend test -Dtest=*EgressProfileRetryPolicyTest*`

## Story 46.3 — Test: nieudana dostawa nie dotyka upstream

status: not-started
depends_on: [Story 46.1, EPIC-14-egress-boundary-ownership/Story 14.2]

Taski:
- [ ] **Test: nieudana próba dostawy nie modyfikuje `payment.payments`/`settlement_finality_records`.**
      `verify: ./mvnw -f backend test -Dtest=*FailedDeliveryDoesNotTouchUpstreamTest*`

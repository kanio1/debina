---
status: not-started
depends_on: [EPIC-09-ownership-schema-grants, EPIC-43-egress-rail-outbound-dispatch]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OWN-6, line 1262); sepa-nexus-blueprint-ownership-integration.md §9 (line 350, 'Egress confirmation & boundary')"
---

# EPIC-14 — Ownership: granica egress (EPIC-OWN-6)

`[FREEZE]` egress zarządza wyłącznie stanem transportu, nigdy finalnością.

## Story 14.1 — `CLAIMED` + `transport_attempts`/`delivery_receipts`

status: not-started
depends_on: []

Taski:
- [ ] **Grant-test: `egress` jedynym writerem `outbound_messages`/`transport_attempts`/`delivery_receipts`.**
      `verify: ./mvnw -f backend test -Dtest=*EgressSchemaOwnershipTest*`

## Story 14.2 — Test: egress nie pisze `payment.status`

status: not-started
depends_on: [Story 14.1]

Taski:
- [ ] **Grant-test: rola `egress` nie ma zapisu na `payment.payments`.**
      `verify: ./mvnw -f backend test -Dtest=*EgressCannotWritePaymentStatusTest*`

## Story 14.3 — Asercja delivered ≠ final

status: not-started
depends_on: [Story 14.1, EPIC-39-settlement-finality-model]

Taski:
- [ ] **Test: dostarczenie (`DELIVERED`) nie zmienia `settlement_finality_records` — finalność ustawia wyłącznie `settlement`.**
      `verify: ./mvnw -f backend test -Dtest=*DeliveredNotFinalTest*`

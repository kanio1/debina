---
status: not-started
depends_on: [EPIC-46-egress-delivery-attempts-retry]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-EGRESS-4, line 1306), [MVP]/[P1]"
---

# EPIC-47 — Egress: potwierdzenia dostawy i pięcioosiowy status (EPIC-EGRESS-4)

## Story 47.1 — `delivery_receipts` + korelacja z artefaktem

status: not-started
depends_on: []

Taski:
- [ ] **Migracja `egress.delivery_receipts` + korelacja z `outbound_artifacts`.**
      `verify: psql -c "\d egress.delivery_receipts"`

## Story 47.2 — `transport_receipts_in` (`[P1]`)

status: not-started
depends_on: [Story 47.1]

Taski:
- [ ] **Migracja `egress.transport_receipts_in`.**
      `verify: psql -c "\d egress.transport_receipts_in"`

## Story 47.3 — Testy pięcioosiowego rozdziału statusów

status: not-started
depends_on: [Story 47.1]

Opis: business ≠ ISO ≠ finality ≠ transport ≠ receipt — `[FREEZE]`.

Taski:
- [ ] **Zestaw testów potwierdzających, że pięć osi statusu nigdy się nie zlewa.**
      `verify: ./mvnw -f backend test -Dtest=*FiveStatusSeparationTest*`

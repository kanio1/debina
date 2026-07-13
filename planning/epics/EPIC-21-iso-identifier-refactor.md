---
status: not-started
depends_on: [EPIC-19-ingress-staging-pipeline]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-CORE-2, line 1249)"
---

# EPIC-21 — Refaktor identyfikatorów ISO (EPIC-CORE-2)

Przeniesienie identyfikatorów z `payments` do `iso.payment_iso_identifiers` (G4 — ISO identity nie może być spłaszczone w stan biznesowy).

## Story 21.1 — Schemat + migracja `iso.payment_iso_identifiers`

status: not-started
depends_on: []

Taski:
- [ ] **Migracja tworząca `iso.payment_iso_identifiers` z kluczem złożonym `(payment_id, source_message_type, iso_message_id)`.**
      `verify: psql -c "\d iso.payment_iso_identifiers"`

## Story 21.2 — Przekierowanie zapytań korelacyjnych

status: not-started
depends_on: [Story 21.1]

Taski:
- [ ] **Wszystkie zapytania korelacyjne przepięte na nową tabelę, usunięte pola identyfikatorów z `payment.payments`.**
      `verify: ./mvnw -f backend test -Dtest=*IdentifierQueryRepointedTest*`

## Story 21.3 — Test lineage per `source_message_type`

status: not-started
depends_on: [Story 21.1]

Taski:
- [ ] **Test: lineage pacs.002/R-message poprawny per `source_message_type`.**
      `verify: ./mvnw -f backend test -Dtest=*LineageBySourceMessageTypeTest*`

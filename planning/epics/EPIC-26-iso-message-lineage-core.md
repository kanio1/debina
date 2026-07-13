---
status: not-started
depends_on: [EPIC-19-ingress-staging-pipeline]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ISO-1, line 1268), [MVP]"
---

# EPIC-26 — ISO: rdzeń lineage wiadomości (EPIC-ISO-1)

## Story 26.1 — `iso.iso_messages` + `iso_message_versions`

status: not-started
depends_on: []

Taski:
- [ ] **Migracja `iso.iso_messages`, `iso.iso_message_versions`** (w tym seed `JSON_DIRECT` z ADR-N7).
      `verify: psql -c "\dt iso.iso_message*"`

## Story 26.2 — `iso.message_lineage`

status: not-started
depends_on: [Story 26.1]

Taski:
- [ ] **Migracja `iso.message_lineage`, rola `ORIGINAL_INSTRUCTION` zapisywana przy każdym przyjęciu.**
      `verify: psql -c "\d iso.message_lineage"`

## Story 26.3 — Bogatsza ekstrakcja identyfikatorów

status: not-started
depends_on: [Story 26.1, EPIC-21-iso-identifier-refactor]

Taski:
- [ ] **Rozszerz ekstrakcję identyfikatorów o pełny zestaw pól `iso.payment_iso_identifiers`.**
      `verify: ./mvnw -f backend test -Dtest=*IdentifierExtractionTest*`

## Story 26.4 — Panel lineage w GraphQL szczegółu płatności

status: not-started
depends_on: [Story 26.2, Story 26.3]

Taski:
- [ ] **Read model GraphQL: timeline lineage + panel identyfikatorów na szczególe płatności.**
      `verify: ./mvnw -f backend test -Dtest=*PaymentLineageGraphQLTest*`

---
status: not-started
depends_on: [EPIC-09-ownership-schema-grants, EPIC-20-payment-lifecycle-fsm]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OWN-3, line 1259); sepa-nexus-blueprint-ownership-integration.md §9 (line 347)"
---

# EPIC-11 — Ownership: cienki wiersz `payments` (EPIC-OWN-3)

`payment-lifecycle` jest jedynym writerem `payment.payments`; status/reason przez FK do katalogów; test strażnika przeciw God-Module.

## Story 11.1 — `payment-lifecycle` jako jedyny writer

status: not-started
depends_on: []

Kryterium ukończenia: grant-test potwierdza wyłączność.

Taski:
- [ ] **Grant-test: tylko rola `payment-lifecycle` pisze do `payment.payments`/`payment_status_history`/`payment_events`.**
      `verify: ./mvnw -f backend test -Dtest=*PaymentSchemaOwnershipTest*`

## Story 11.2 — Status/reason przez FK do katalogu

status: not-started
depends_on: [Story 11.1]

Kryterium ukończenia: kolumny status/reason są FK do `reference_data`, nie wolnym tekstem.

Taski:
- [ ] **Migracja: `status`/`reason_code` jako FK do katalogów `reference_data.status_catalog`/`reference_data.iso_reason_codes`.**
      `verify: psql -c "\d payment.payments"` → kolumny status/reason mają ograniczenie FK.

## Story 11.3 — Test strażnika God-Module

status: not-started
depends_on: [EPIC-09-ownership-schema-grants/Story 9.4]

Opis: `payment-lifecycle` nie pisze bezpośrednio do `settlement`/`routing`/`egress` (dokument OWN precyzuje to explicite).

Kryterium ukończenia: reguła ArchUnit wymuszona.

Taski:
- [ ] **Reguła ArchUnit: `payment-lifecycle` nie ma zapisu do `settlement`/`routing`/`egress`.**
      `verify: ./mvnw -f backend test -Dtest=*PaymentNoGodModuleTest*`

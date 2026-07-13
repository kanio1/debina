---
status: not-started
depends_on: [EPIC-08-walking-skeleton-verification]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-IN-1, line 1246); ADR-N7 (JSON_DIRECT)"
---

# EPIC-19 — Ingress: staging pipeline (EPIC-IN-1)

Pierwszy prawdziwy epik domenowy Iteracji 1 — rozszerza cienki submit z EPIC-06/EPIC-08 o idempotencję na poziomie domeny, signature-before-parse i kanał XML.

## Story 19.1 — REST JSON submit + idempotencja (JSON_DIRECT, ADR-N7)

status: not-started
depends_on: []

Opis: Controller→IdempotencyStore (dwukrokowy, PG18)→IngestionService (TX)→outbox→happy-path. Realizuje ADR-N7: `JSON_DIRECT` jako seedowany pseudo-message-version, `iso.iso_messages(parse_status='SKIPPED')`.

Kryterium ukończenia: powtórzone zgłoszenie z tym samym kluczem idempotencji zwraca ten sam `paymentId`/409, identyfikatory zapisane przez `iso.payment_iso_identifiers` nawet dla JSON.

Taski:
- [ ] **Rozszerz `PaymentController`/`PaymentService` z EPIC-03 o `IdempotencyStore`** (dwukrokowy zapis PG18) i tworzenie wiersza `iso.iso_messages(direction=INBOUND, message_type='JSON_DIRECT', parse_status='SKIPPED')` + `iso.payment_iso_identifiers` + `iso.message_lineage(lineage_role='ORIGINAL_INSTRUCTION')` dokładnie wg przepływu z ADR-N7.
      `verify: ./mvnw -f backend test -Dtest=*JsonDirectIngestionTest*` → powtórzone zgłoszenie z tym samym idempotency key zwraca ten sam payment id / 409; identyfikatory obecne w `iso.payment_iso_identifiers`.

## Story 19.2 — Filtr signature-before-parse

status: not-started
depends_on: [EPIC-31-signature-module]

Opis: kolejność egzekwowana jako test na łańcuchu filtrów, nie tylko dokumentacja (G1).

Taski:
- [ ] **Wpięcie `SignatureVerificationPort` w łańcuch filtrów przed jakimkolwiek parsowaniem XML dla kanałów bankowych/plikowych.**
      `verify: ./mvnw -f backend test -Dtest=*SignatureBeforeParseOrderingTest*`

## Story 19.3 — Hartowanie XML

status: not-started
depends_on: []

Opis: konfiguracja odporna na XXE/bomby, z fixture'ami negatywnymi.

Taski:
- [ ] **Skonfiguruj parser XML odporny na XXE/entity-expansion, z fixture'ami negatywnymi (XXE payload, billion-laughs).**
      `verify: ./mvnw -f backend test -Dtest=*XmlHardeningTest*`

## Story 19.4 — REST XML pain.001

status: not-started
depends_on: [Story 19.3]

Opis: taksonomia błędów 422 dla nieprawidłowego pain.001.

Taski:
- [ ] **Endpoint REST XML pain.001 z taksonomią błędów 422 (XML hardening result → `iso_message_parse_errors`).**
      `verify: ./mvnw -f backend test -Dtest=*Pain001XmlSubmissionTest*`

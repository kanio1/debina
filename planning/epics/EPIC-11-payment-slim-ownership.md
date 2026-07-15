---
status: in-progress
depends_on: [EPIC-09-ownership-schema-grants, EPIC-20-payment-lifecycle-fsm]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OWN-3, line 1259); sepa-nexus-blueprint-ownership-integration.md §9 (line 347)"
---

# EPIC-11 — Ownership: cienki wiersz `payments` (EPIC-OWN-3)

`payment-lifecycle` jest jedynym writerem `payment.payments`; status/reason przez FK do katalogów; test strażnika przeciw God-Module.

`[REEWALUACJA 2026-07-15]`: `EPIC-21` Story 21.2 usunęła `end_to_end_id` z `payment.payments` — `payments` jest dziś strukturalnie cieńszy, kierunkowo zgodnie z celem tego epika. To nie odblokowuje żadnej story tutaj: Story 11.1's `verify:` wymaga grant-testu na `payment.payments`/`payment_status_history`/`payment_events` — dwie ostatnie tabele wciąż nie istnieją nigdzie w repo (OQ-12, `planning/README.md` punkt 12, bez właściciela epika/story). Story 11.2 (status/reason jako FK) i 11.3 (God-Module guard) niezwiązane z EPIC-21, bez zmian.

`[OQ-12 ROZSTRZYGNIĘTE 2026-07-15]`: patrz `planning/README.md` punkt 15b (ownership matrix) i Story 11.1 poniżej — `payment_status_history`/`payment_events` dostały właściciela (ten epik/tę story) i implementację. `EPIC-24` Story 24.2 timeline odblokowany tym samym rozstrzygnięciem.

## Story 11.1 — `payment-lifecycle` jako jedyny writer

status: done

depends_on: []

Kryterium ukończenia: grant-test potwierdza wyłączność.

Taski:
- [x] **Grant-test: tylko rola `payment-lifecycle` pisze do `payment.payments`/`payment_status_history`/`payment_events`.**
      `verify: ./mvnw -f backend test -Dtest=PaymentHistoryOwnershipTest` → `5/5 PASS` (2026-07-15). Nazwa klasy testu skorygowana względem oryginalnego `verify:` (`*PaymentSchemaOwnershipTest*` nigdzie nie istniał w repo, prawdopodobnie literówka/planowana-ale-nieużyta nazwa) — zachowując dokładnie ten sam zakres: `sepa_app` może `INSERT` do wszystkich trzech tabel, `signature_role` (obca rola modułu) nie może, nikt nie może `UPDATE`/`DELETE` (append-only, `REVOKE` w V19). `payment.payments`' istniejący grant-test (`SchemaGrantMatrixTest`) niezmieniony — ten test rozszerza pokrycie o dwie NOWE tabele.

      **Schema** (`V19__payment_status_history_and_events.sql`, `V20__backfill_payment_status_history_baseline.sql`): `payment.payment_status_history` (klucz złożony `(payment_id, seq)`, append-only, `is_final`/`event_type`/`event_ref` do korelacji z `payment_events`/outbox), `payment.payment_events` (domenowy event-log, `id`=`gen_random_uuid()`, `payload jsonb`, BRIN+GIN indeksy). Świadomie **bez** `REFERENCES payment.payments(id)` — dokładnie ten sam wzorzec co `iso.payment_iso_identifiers`/`iso.message_lineage` (V11), bo JPA `paymentRepository.save()` nie flush'uje przed kolejnym surowym `JdbcTemplate`-insertem w tej samej transakcji (odkryte przez realny test failure, nie teoretyczne ryzyko). Świadomie bez partycjonowania (wzorzec V10 `ingress.raw_inbound_messages`) i bez `tenant_id`/RLS na samych tabelach (źródło ich nie ma — widoczność przez join-through-`payments`, które ma RLS). Backfill V20 daje każdej istniejącej płatności dokładnie jeden wiersz `MIGRATION_BASELINE`, `at`=czas obserwacji migracji (nie `created_at` — nie fabrykuje fałszywej historii).

      **Runtime**: `PaymentHistoryRecorder` (nowy) pisze zarówno `payment_events` jak i `payment_status_history` atomowo w tej samej transakcji co `payment.payments`/outbox insert (`PaymentCreationWriter`) i jako część tej samej transakcji co status transition (`InboxConsumer.consume`). Jeden `UUID eventId` współdzielony między `PaymentLifecycleEvent.eventId()`, `payment_events.id` i `payment_status_history.event_ref` (domain event identity unification). Nielegalne przejście FSM rzuca `IllegalPaymentTransitionException` i rolluje całą transakcję (żaden wiersz history/event nie zostaje osierocony) — potwierdzone testem wywołującym `InboxConsumer.consume(payload)` bezpośrednio.

      Story 11.3 (God-Module ArchUnit guard) pozostaje `not-started` — nie jest wymagana do ochrony tego vertical slice (history/events nie dotykają `settlement`/`routing`/`egress`) i nie została rozpoczęta w tej sesji.

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

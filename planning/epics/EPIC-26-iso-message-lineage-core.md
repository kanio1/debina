---
status: in-progress
depends_on: [EPIC-19-ingress-staging-pipeline]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-ISO-1, line 1268), [MVP]"
---

# EPIC-26 — ISO: rdzeń lineage wiadomości (EPIC-ISO-1)

`[PLANNING-DEFECT 2026-07-14]`: wybrano ten epik priorytetowo (przed innymi nowymi kandydatami) zgodnie z zasadą "dokończ epik, który faktycznie odblokuje story wcześniejszego epika `in-progress`" — `EPIC-20` Story 20.3 wymienia `EPIC-26` w `depends_on`. Story 26.1/26.2 okazały się **już w pełni zbudowane** przez `EPIC-19` Story 19.1 (te same migracje `V11__iso_json_direct_lineage.sql` tworzą dokładnie `iso.iso_messages`/`iso.iso_message_versions`/`iso.message_lineage`) — potwierdzone tu ponownie jako WŁASNY deliverable tego epika (nie duplikacja praktyki, tylko formalne domknięcie właściwego epika za pomocą już-istniejącego artefaktu). Story 26.3/26.4 pozostają `blocked` — patrz niżej.

**Ważne dla `EPIC-20` Story 20.3**: mimo że `EPIC-26` (poziom epika) jest teraz zamknięty na tyle, na ile jest to dziś wykonalne, **Story 20.3 pozostaje `blocked`** — literalne `depends_on: EPIC-26` jest spełnione, ale rzeczywista zdolność potrzebna do korelacji statusu przychodzącego (silnik korelacji 9-krokowy, realny kanał inbound status jak `csm.response.received`/pacs.002) należy do `EPIC-27` (Silnik korelacji ISO, `depends_on: EPIC-26`, wciąż `not-started`) i do modułu `simulation` (który wciąż ma zero kodu, patrz `EPIC-17`). Zgodnie z zasadą "story można odblokować wyłącznie gdy brakujący element istnieje rzeczywiście w kodzie" — nie odblokowuję Story 20.3 tylko dlatego, że `EPIC-26` formalnie zamknięty; brakująca zdolność nadal nie istnieje.

## Story 26.1 — `iso.iso_messages` + `iso_message_versions`

status: done
depends_on: []

Taski:
- [x] **Migracja `iso.iso_messages`, `iso.iso_message_versions`** (w tym seed `JSON_DIRECT` z ADR-N7).
      `verify: podman exec -i infra_postgres_1 psql -U sepa_migration -d sepa_nexus -c "\dt iso.iso_message*"` → dwie tabele (`iso_message_versions`, `iso_messages`) — PASS (2026-07-14, zbudowane w `EPIC-19` Story 19.1, `V11__iso_json_direct_lineage.sql`, potwierdzone tu jako właściwy deliverable tego epika).

## Story 26.2 — `iso.message_lineage`

status: done
depends_on: [Story 26.1]

Taski:
- [x] **Migracja `iso.message_lineage`, rola `ORIGINAL_INSTRUCTION` zapisywana przy każdym przyjęciu.**
      `verify: podman exec -i infra_postgres_1 psql -U sepa_migration -d sepa_nexus -c "\d iso.message_lineage"` → tabela istnieje z FK do `iso.iso_messages`/`ingress.raw_inbound_messages` — PASS (2026-07-14). Potwierdzono również substancjalnie (nie tylko kształt schematu): `SELECT lineage_role, count(*) FROM iso.message_lineage GROUP BY lineage_role` → `ORIGINAL_INSTRUCTION: 1` (z realnego smoke-testu EPIC-22 przeciw żywej infrastrukturze) — dowodzi, że rola faktycznie jest zapisywana przy przyjęciu, nie tylko że kolumna istnieje.

## Story 26.3 — Bogatsza ekstrakcja identyfikatorów

status: done
depends_on: [Story 26.1, EPIC-21-iso-identifier-refactor (Story 21.1/21.3 only — see below)]

`[UNBLOCKED 2026-07-15]`: ten story był jawnie warunkowy — "odblokuj razem z pierwszym realnym kanałem XML" — i ten kanał (`EPIC-19` Story 19.4, pain.001) powstał w tej sesji. Capability-first per `planning/README.md` zasada: `depends_on` wskazuje cały `EPIC-21`, ale rzeczywista potrzebna zdolność to wyłącznie schemat (Story 21.1, `done`) — Story 21.2 (usunięcie identyfikatorów z `payment.payments`, wciąż `blocked`) nie jest wymagana do rozszerzenia ekstrakcji identyfikatorów w `iso.payment_iso_identifiers`, więc nie blokuję tego story całym epikiem.

`[ZAKRES, doprecyzowane]`: "pełny zestaw pól" per §4.3c to `MsgId`/`PmtInfId`/`InstrId`/`EndToEndId`/`TxId`/`UETR`/`Orgnl*`. Zbudowano wyłącznie pola, które kanał pain.001 rzeczywiście niesie: `MsgId` (GrpHdr), `PmtInfId`, `InstrId` (opcjonalne), `EndToEndId`, `UETR` (opcjonalne, pain.001.001.09+). `TxId` **nie jest polem pain.001** (`PaymentIdentification6` nie ma `TxId` — to koncept `pacs.008`); `Orgnl*` to korelacja R-message/status (`EPIC-27`, `not-started`). Żadne z tych dwóch nie zostało dodane jako puste/syntetyczne kolumny — czekają na kanał, który je rzeczywiście niesie (`pacs.008` dla `TxId`, `pacs.002`/R-message dla `Orgnl*`), zgodnie z zasadą "nie generuj brakujących identyfikatorów".

Taski:
- [x] **Rozszerz ekstrakcję identyfikatorów o pełny zestaw pól `iso.payment_iso_identifiers` dostępny dla danego kanału.**
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=Pain001SubmissionEndpointTest#validSignedPain001CreatesPaymentWithIdentifiersLineageAndOutbox` → PASS (2026-07-15) — asercja SQL bezpośrednio na `iso.payment_iso_identifiers` potwierdza `msg_id`/`pmt_inf_id`/`end_to_end_id` zapisane dla realnego, podpisanego kanału XML (migracja `V15__iso_pain001_identifier_fields.sql`, `Pain001LineageRecorder`).

## Story 26.4 — Panel lineage w GraphQL szczegółu płatności

status: blocked
depends_on: [Story 26.2, Story 26.3]

`[PLANNING-DEFECT 2026-07-14]`: GraphQL **nie istnieje w ogóle** w tym repo (potwierdzone wcześniej w `EPIC-16`: brak `spring-graphql`/`graphql-java` w `backend/pom.xml`) — budowanie panelu GraphQL teraz byłoby wynajdywaniem architektury na wyrost, w dodatku Story 26.3 (od którego to zależy) też jest `blocked`. **Status `blocked`** — odblokuj razem z pierwszym epikiem budującym warstwę GraphQL (prawdopodobnie `EPIC-16`/frontend-owy epik read-model).

Taski:
- [ ] **Read model GraphQL: timeline lineage + panel identyfikatorów na szczególe płatności.**
      `verify: ./mvnw -f backend test -Dtest=*PaymentLineageGraphQLTest*` — `NOT RUN`, `blocked` (patrz wyżej).

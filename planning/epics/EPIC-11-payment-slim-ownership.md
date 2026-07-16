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

status: blocked
depends_on: [Story 11.1]

`[CAPABILITY-BLOCKED 2026-07-16 — main consolidation/EPIC-11 readiness audit session]`: potwierdzone w repozytorium (`grep -Rni "status_catalog|iso_reason_codes"` po `backend/src/main`, `backend/src/test`, `backend/src/main/resources/db/migration`, `planning`), że `reference_data.status_catalog`/`reference_data.iso_reason_codes` **nie istnieją w żadnej migracji Flyway** — jedyne trafienie w produkcyjnym kodzie to komentarz w `V19__payment_status_history_and_events.sql` (linia 21-23) dokumentujący, że `status_code`/`reason_code` na `payment.payment_status_history` są dziś plain text, "FK-able" dopiero po powstaniu tych katalogów. Źródło (`sepa-nexus-message-flow-and-data-blueprint.md` §4.13, linie 891/894) ma pełny, source-approved DDL sketch dla obu tabel, oznaczony `[MVP]` — **nie** `[NO-CODE]` — więc to nie jest kwestia sekwencjonowania źródła jak `EPIC-12` Story 12.2's `validation_profiles`/`mapping_profiles`/`render_profiles`. Problem jest planistyczny: `sepa-nexus-blueprint-ownership-integration.md` (linia 348) i `sepa-nexus-message-flow-and-data-blueprint.md` (linia 1260) definiują `EPIC-OWN-4` jako cztery sub-story (S1 `participants`/`participant_accounts`, S2 `iso_reason_codes`/`status_catalog`+loaders, S3 `business_calendars`/`service_levels`/`scheme_profiles`/`settlement_cutoff_calendar`, S4 grant test), ale `planning/epics/EPIC-12-reference-data-ownership.md` dziś ma tylko dwie stories: 12.1 (odpowiada S3, `done`, `service_levels` świadomie pominięty) i 12.2 (walidacja/mapowanie/renderowanie z R-09, `[NO-CODE]`-blocked do Iteracji 5) — **żadna story nie jest właścicielem S1 ani S2**. Brak DDL dla `status_catalog`/`iso_reason_codes` nie wynika więc z tego, że są odłożone do przyszłej iteracji świadomie — po prostu żaden istniejący epik/story ich dziś nie obejmuje.

Brakujące capabilities przed startem tej story:
- właściciel-story dla `EPIC-OWN-4` S2 (`iso_reason_codes`/`status_catalog` + loaders) w `EPIC-12` (dziś nie istnieje — planning gap, nie decyzja o odłożeniu);
- migracja Flyway tworząca obie tabele (source DDL §4.13 istnieje, ale nie zaimplementowana);
- seed/loader dla obu katalogów (źródło mówi "+ loaders", szczegóły loadera nie rozstrzygnięte tutaj).

Nie twórz tych tabel ani nowej story w `EPIC-12` w tej sesji (poza zakresem — `PRODUCTION_CODE_CHANGES` tej sesji ograniczone do `EPIC-11` Story 11.3, `DATABASE_MIGRATIONS: FORBIDDEN`, "nowych katalogów reference_data" jawnie wykluczone). Zapisz jako `[OPEN-QUESTION]` do rozstrzygnięcia w przyszłej sesji: czy `EPIC-12` dostaje nową Story 12.3 dla S1+S2, czy S2 zostaje przeniesiona bezpośrednio jako zależność tej story.

Kryterium ukończenia: kolumny status/reason są FK do `reference_data`, nie wolnym tekstem.

Taski:
- [ ] **Migracja: `status`/`reason_code` jako FK do katalogów `reference_data.status_catalog`/`reference_data.iso_reason_codes`.**
      `verify: psql -c "\d payment.payments"` → kolumny status/reason mają ograniczenie FK. `NOT RUN` — `[CAPABILITY-BLOCKED]`, brak katalogów-owner-story (patrz wyżej).

## Story 11.3 — Test strażnika God-Module

status: done
depends_on: [EPIC-09-ownership-schema-grants/Story 9.4]

Opis: `payment-lifecycle` nie pisze bezpośrednio do `settlement`/`routing`/`egress` (dokument OWN precyzuje to explicite).

Kryterium ukończenia: reguła ArchUnit wymuszona.

`[DONE 2026-07-16 — main consolidation session]`: żaden z `settlement`/`routing`/`egress` nie istnieje jeszcze w kodzie (`find backend/src/main/java/com/sepanexus/modules -maxdepth 4 -type d` → tylko `paymentlifecycle`; ten sam finding co `epic10.IsoAdapterNoBusinessDecisionTest`'s własny komentarz dla tych samych trzech modułów), a `grep` po `payment-lifecycle` production code za `(INSERT INTO|UPDATE|DELETE FROM)\s+(settlement|routing|egress)\.` i za samymi nazwami modułów → zero trafień. Reguła wobec prawdziwego kodu przechodzi dziś wyłącznie dlatego, że nie ma czego złamać — bez dowodu non-vacuous byłby to fałszywy PASS.

Nowy `backend/src/test/java/com/sepanexus/PaymentNoGodModuleTest.java`: reguła ArchUnit oparta na granicach pakietów (`noClasses().that().resideInAPackage("..paymentlifecycle..").should().dependOnClassesThat().resideInAnyPackage("..settlement.internal..", "..routing.internal..", "..egress.internal..")`) — `.internal` jako zabroniony pakiet implementacyjny modułu, pakiet-root jako dozwolony publiczny port, dokładnie ta sama konwencja co już istniejący prawdziwy moduł `signature` (`SignaturePort` vs `signature.internal`). Odporna na zmianę nazwy klasy (oparta na strukturze pakietów, nie na dopasowaniu nazwy `Settlement`/`Routing`/`Egress`).

Non-vacuous proof (Część 13): nowe test-only fixture pod `backend/src/test/java/com/sepanexus/architecturefixtures/` — `paymentlifecycle/forbidden/{ForbiddenSettlementCaller,ForbiddenRoutingCaller,ForbiddenEgressCaller}` (każdy zależy wprost od odpowiedniego `<module>/internal/Internal<Module>Repository`), `paymentlifecycle/allowed/AllowedSettlementPortCaller` (zależy wyłącznie od `settlement/SettlementPort`, publicznego portu). 5 testów: (1) produkcyjny scope (`backend/src/main/java` only, `DO_NOT_INCLUDE_TESTS`) → PASS; (2)-(4) reguła uruchomiona przeciw fixture forbidden dla settlement/routing/egress → każdy `EvaluationResult.hasViolation()==true`, komunikat naruszenia zawiera nazwę zależnej klasy i zabronionego celu (potwierdzone `assertThat(details).contains(...)`); (5) reguła przeciw fixture allowed (publiczny port) → `hasViolation()==false`.

Test-first: RED potwierdzony (`-Dtest=*PaymentNoGodModuleTest*` → `BUILD FAILURE`, "No tests matching pattern" — klasa jeszcze nie istniała) przed utworzeniem plików. GREEN po implementacji: `5/5 PASS`. Mutation-proof 2x: (1) usunięcie `"..settlement.internal.."` z listy zabronionych pakietów → dokładnie `ruleCatchesForbiddenDependencyOnSettlementImplementation` poprawnie FAIL (pozostałe 4 nadal PASS) → cofnięte; (2) rozszerzenie zabronionego celu z `"..settlement.internal.."` na `"..settlement.."` (usunięcie wyjątku dla publicznego portu) → dokładnie `ruleAllowsDependencyOnPublicSettlementPort` poprawnie FAIL → cofnięte. Po obu cofnięciach: `5/5 PASS`, `git diff --check` czyste, brak pozostałości mutacji.

Sąsiednie testy architektoniczne (`IsoAdapterNoBusinessDecisionTest`, `SignatureNoForeignRepoAccessTest`, `OwnershipArchRulesTest`, `ModularityTest`) niezmienione, wszystkie nadal PASS (`13/13` razem z nowym testem). Pełny regres backendu: `219/219 PASS` (było `214/214` przed tą story), `BUILD SUCCESS`. Znany nieszkodliwy scheduler WARN "permission denied for schema iso" (7x) — ten sam udokumentowany wyścig `IsoOutboxDispatcher`/`SetLocalRoleSqlProofTest`, brak nowych failures.

Taski:
- [x] **Reguła ArchUnit: `payment-lifecycle` nie ma zapisu do `settlement`/`routing`/`egress`.**
      `verify: ./mvnw -f backend test -Dtest=*PaymentNoGodModuleTest*` → `Tests run: 5, Failures: 0, Errors: 0` — PASS (2026-07-16).

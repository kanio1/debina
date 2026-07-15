---
status: in-progress
depends_on: [EPIC-09-ownership-schema-grants, EPIC-26-iso-message-lineage-core, EPIC-21-iso-identifier-refactor]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OWN-2, line 1258); sepa-nexus-blueprint-ownership-integration.md §9 (line 346, 'ISO lineage split')"
---

# EPIC-10 — Ownership: ISO lineage split (EPIC-OWN-2)

Wymuszenie, że `iso-adapter` jest jedynym właścicielem tabel lineage/identyfikatorów, i że nie podejmuje decyzji biznesowych.

`[REEWALUACJA 2026-07-15]`: `EPIC-21` (jeden z trzech `depends_on`) jest teraz `done` — `payment.payments.end_to_end_id` usunięty, `iso.payment_iso_identifiers` jest jedynym miejscem przechowywania identyfikatorów ISO. To realnie przybliża **Story 10.2** (korelacja wyłącznie przez `iso.payment_iso_identifiers`, nigdy przez pole na `payment.payments`) — ta struktrualna gwarancja jest teraz bezwarunkowo prawdziwa (nie ma już alternatywnego pola do sprawdzenia). Mimo to Story 10.2 pozostaje `not-started`: jej `verify:` wymaga integracyjnego testu **korelacji pacs.002**, a silnik korelacji (`EPIC-27`, `not-started`) wciąż nie istnieje — nie ma czego testować end-to-end, tylko strukturalną przesłankę. Story 10.1 (wyłączność zapisu roli `iso-adapter` do schematu `iso`) nadal `not-started` — `iso-adapter` nadal nie jest osobnym modułem/rolą DB (`sepa_app` nadal pisze do obu `payment` i `iso`). Story 10.3 niezmieniona (zależy od `EPIC-09` Story 9.4, niezwiązana z EPIC-21). Nie rozpoczynano implementacji EPIC-10 w tej sesji.

`[REEWALUACJA 2026-07-15 — druga sesja, EPIC-11/EPIC-24 timeline]`: przy okazji budowy `EPIC-11` Story 11.1 (`PaymentHistoryRecorder`) i wcześniej `EPIC-19` Story 19.4 (`Pain001CanonicalMapper`) potwierdzony realny, uruchomiony dziś wzorzec: `PaymentService.submitPayment` jest jedną `@Transactional` metodą, która wewnątrz jednej transakcji JDBC pisze do `payment.payments`, `payment.outbox_events`, `payment.payment_status_history`, `payment.payment_events` (schemat `payment`) **oraz** do `iso.iso_messages`/`iso.payment_iso_identifiers`/`iso.message_lineage` (schemat `iso`, przez `JsonDirectLineageRecorder`/`Pain001CanonicalMapper`) — dziś to działa bezproblemowo, bo obie grupy tabel są dziś pisane przez tę samą rolę DB (`sepa_app`) w tym samym połączeniu, w ramach jednego deployowalnego Spring Modulith. `[OPEN-QUESTION] EPIC-10 — koordynacja transakcji jeden-writer-per-schema`: żaden przeczytany dokument źródłowy (`sepa-nexus-blueprint-ownership-integration.md` §3.6/§9, `sepa-nexus-message-flow-and-data-blueprint.md` §8) nie rozstrzyga, co ma się stać z tym dokładnym wzorcem **po** Story 10.1. Nie rozstrzygnięto tutaj samodzielnie — zapisane też w `planning/README.md` jako otwarte pytanie. Nie rozpoczęto implementacji EPIC-10 w tej sesji.

`[REEWALUACJA 2026-07-15 — trzecia sesja, transaction-coordination proof gate]`: przeprowadzono lokalny, odwracalny proof (Część A-D packetu tej sesji, `backend/src/test/java/com/sepanexus/epic10/SetLocalRoleSqlProofTest.java` 7/7 PASS, `SetLocalRoleJpaFlushProofTest.java` 2/2 PASS — oba zachowane jako trwałe testy dokumentujące dowód, nigdy nie wpięte w kod produkcyjny). Wynik: `SET LOCAL ROLE` technicznie zachowuje jedną transakcję/`txid`, poprawną izolację uprawnień i poprawny rollback, ale ma udowodniony hazard — odroczony zapis JPA (`payment.payments` przez `GenerationType.UUID`, w pamięci od razu, flush odroczony) wykonuje się pod rolą aktywną w momencie flush/commit, NIE pod rolą aktywną w momencie `save()`; przełączenie roli w międzyczasie powoduje `42501` w commit, cofający też pozornie już-zapisane wiersze innej roli. Dodatkowo znaleziono silny, już-istniejący precedens w realnym kodzie: moduł `signature` (jedyny dziś z prawdziwie osobną rolą DB, `signature_role` — potwierdzone real-DB audytem read-only, zero grantów krzyżowych z `payment`/`iso`) NIE dzieli jednej transakcji z `payment-lifecycle` — `SignatureConnectionFactory` otwiera osobne, surowe połączenie JDBC, sekwencyjnie przed atomowym ogonem, z wcześniejszym etapem trwałym niezależnie od późniejszej porażki (potwierdzone wprost przez javadoc `Pain001IngestionService`). Pełny zapis dowodów, macierzy decyzyjnej i rekomendacji (nie decyzji): `EPIC-10-transaction-coordination-decision-memo.md` (status `PROPOSED — REQUIRES USER DECISION`). Story 10.1 pozostaje `blocked` do czasu decyzji użytkownika — proof NIE jest równoznaczny z wdrożeniem, żadna migracja `V21` nie powstała, żadne granty na realnej `infra_postgres_1` nie zostały zmienione.

## Story 10.1 — `iso.payment_iso_identifiers` + `message_lineage` jako własność `iso-adapter`

status: blocked

depends_on: []

Opis: potwierdzenie ownership schematu `iso` po migracji z EPIC-21.

`[BLOCKED 2026-07-15]`: wydzielenie `iso-adapter` jako osobnej roli DB usuwa `sepa_app`'owe prawo zapisu do `iso.*`, co łamie dzisiejszą jedno-transakcyjną atomowość `PaymentService.submitPayment`/`Pain001PersistenceService.persist` (patrz `[REEWALUACJA — trzecia sesja]` wyżej). Lokalny proof mechanizmu zastępczego (`SET LOCAL ROLE`) wykonany i udokumentowany (`EPIC-10-transaction-coordination-decision-memo.md`), ale wybór mechanizmu wymaga decyzji użytkownika, nie tylko dowodu wykonalności — nie rozpoczynać tej story do czasu tej decyzji.

Kryterium ukończenia: tylko rola `iso-adapter` pisze do `iso.*`.

Taski:
- [ ] **Grant-test potwierdzający wyłączność zapisu `iso-adapter` do schematu `iso`.**
      `verify: ./mvnw -f backend test -Dtest=*IsoSchemaOwnershipTest*` — `NOT RUN`, `blocked` (patrz wyżej).

## Story 10.2 — Korelacja pacs.002 przez lineage

status: not-started
depends_on: [Story 10.1]

Opis: zapytania korelacyjne przechodzą wyłącznie przez `iso.payment_iso_identifiers`.

Kryterium ukończenia: brak alternatywnej ścieżki korelacji poza lineage.

Taski:
- [ ] **Test integracyjny: korelacja pacs.002 wyłącznie przez `iso.payment_iso_identifiers`, nie przez pole na `payment.payments`.**
      `verify: ./mvnw -f backend test -Dtest=*IsoCorrelationOwnershipTest*`

## Story 10.3 — `iso-adapter` nie podejmuje decyzji biznesowej (arch test)

status: done

depends_on: [EPIC-09-ownership-schema-grants/Story 9.4]

Opis: `iso-adapter` nigdy nie zmienia `payment.status`, nie routuje, nie rozlicza.

Kryterium ukończenia: reguła ArchUnit wymuszona.

Taski:
- [x] **Reguła ArchUnit: pakiet `iso-adapter` nie wywołuje niczego, co zmienia `payment.status`/routing/settlement.**
      `verify: ./mvnw -f backend test -Dtest=IsoAdapterNoBusinessDecisionTest` → `3/3 PASS` (2026-07-15). `backend/src/test/java/com/sepanexus/epic10/IsoAdapterNoBusinessDecisionTest.java` — trzy reguły ArchUnit (scope: `com.sepanexus.modules.paymentlifecycle.isoadapter..`, produkcyjny kod wyłącznie, `ImportOption.Predefined.DO_NOT_INCLUDE_TESTS` — bez tego test-fixture'y (np. `LineageBySourceMessageTypeTest` czytające `payment.getId()`) dawały fałszywe naruszenie): (1) `iso-adapter` nigdy nie zależy od `PaymentEntity` — a wywołanie `markValidated()` wymaga uprzedniej zależności od tej klasy, więc ta jedna reguła zabrania też metody bezpośrednio; (2) nigdy nie zależy od `PaymentTransitionTable` (tabela reguł FSM); (3) nigdy nie zależy od `PaymentRepository` (jedyny writer `payment.payments`). `routing`/`settlement`/`ledger`/`egress` świadomie bez reguły — te pakiety nie istnieją jeszcze w kodzie (ten sam `[PLANNING-DEFECT]` co `EPIC-09` Story 9.4 — reguła przeciw nieistniejącemu pakietowi byłaby albo próżna, albo wynajdywaniem architektury). Wszystkie trzy reguły potwierdzone jako nie-próżne (non-vacuous) przez tymczasową, deliberate-violation klasę w pakiecie `isoadapter` dla każdej z trzech (import `PaymentEntity`/`PaymentTransitionTable`/`PaymentRepository`) → `BUILD FAILURE` (EXPECTED FAIL) za każdym razem, usunięte po potwierdzeniu; `grep -rn MUTATION-TEST-TEMP backend/src` → czysto.

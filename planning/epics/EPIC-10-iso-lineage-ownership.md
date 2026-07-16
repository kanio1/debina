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

`[REEWALUACJA 2026-07-16 — czwarta sesja, SECURITY DEFINER proof gate]`: dedykowana sesja lokalnego, odwracalnego proofu `SECURITY DEFINER` jako alternatywy dla `SET LOCAL ROLE`. Pięć nowych, trwałych klas testowych (`backend/src/test/java/com/sepanexus/epic10/SecurityDefinerPrivilegeProofTest.java`, `SecurityDefinerTransactionProofTest.java`, `SecurityDefinerSearchPathProofTest.java`, `SecurityDefinerPoolIsolationProofTest.java`, `SecurityDefinerJpaFlushProofTest.java`) — **18/18 PASS**. Kluczowy wynik: hazard JPA odkryty dla `SET LOCAL ROLE` **nie występuje** dla `SECURITY DEFINER`, ponieważ `current_user` callera nigdy realnie się nie zmienia poza czasem trwania samego wywołania funkcji (potwierdzone: `session_user` niezmienny cały czas, `current_user` wraca do callera natychmiast po powrocie z funkcji, wciąż w tej samej transakcji) — dowiedzione zarówno bezpośrednio (`SecurityDefinerTransactionProofTest`), jak i przez odtworzenie dokładnie tego samego scenariusza JPA co w `SetLocalRoleJpaFlushProofTest`, tym razem bez błędu. Dodatkowo wykonano i potwierdzono realny atak `search_path` na `SECURITY DEFINER` (klasyczna podatność klasy CVE-2007-2138) — funkcja bez jawnego `SET search_path` faktycznie przejmowana przez funkcję podstawioną w `public` przez nieuprzywilejowaną rolę; funkcja z `SET search_path = proof_iso, pg_temp` odporna. Przy okazji odkryto i udokumentowano istotny, wcześniej nieoczywisty szczegół Postgresa: samo `EXECUTE` na funkcji nie wystarcza — caller potrzebuje też `USAGE` na schemacie, by w ogóle zakwalifikować nazwę funkcji. **Rekomendacja poprzedniej sesji (outbox/eventual-consistency) zrewidowana**: sprawdzono realny kod read-modelu (`PaymentService.visiblePayments`/`paymentDetail`) i potwierdzono, że dziś **wymaga** synchronicznego lineage — brak jakiegokolwiek `null`-tolerant/`LINEAGE_PENDING` zachowania, brakujący identyfikator dla JEDNEJ płatności psuje CAŁĄ listę (`500`), nie tylko ten jeden wiersz. To oznacza, że przejście na outbox/eventual-consistency wymagałoby realnego przeprojektowania read-modelu, czego wcześniejsza rekomendacja nie uwzględniała — dokładnie to ostrzeżenie przed automatycznym uogólnianiem precedensu `signature` (który ma inną semantykę trwałości — evidence przetrwałe niezależnie od payment, nigdy nie czytane przez read model powodzenia), które ta sesja miała jawnie zweryfikować (packet §14), potwierdziło się jako realne ryzyko. Nowa rekomendacja: **`SECURITY DEFINER`** — jedyny dotąd zbadany mechanizm spełniający jednocześnie wszystkie bramki packetu §40 (jedna transakcja, atomowy rollback we wszystkich kierunkach, brak hazardu JPA, wąski `EXECUTE`, `PUBLIC` odwołane, zademonstrowany i obroniony atak `search_path`, właściciel funkcji `NOLOGIN`/nie-superuser/nie-`BYPASSRLS`, zachowane GUC/pool). Wciąż `PROPOSED — REQUIRES USER ACCEPTANCE`, nie decyzja. Pełny zapis: `EPIC-10-transaction-coordination-decision-memo.md`. Story 10.1 pozostaje `blocked` — mechanizm technicznie dowiedziony, implementacja produkcyjna nie rozpoczęta, żadna migracja `V21` nie powstała, żadne granty na `infra_postgres_1` nie zostały zmienione. `EPIC-27` nie rozpoczęty.


`[SPLIT 2026-07-16 — dual-agent governance/backlog-redesign session, H9]`: this story used to bundle a technical proof, a pending user decision, command-API design, DB function/role design, a production migration, `JSON_DIRECT` integration, `pain.001` integration, and privilege/search_path/rollback verification all under one story/checkbox. Evidence: the decision memo's own "Conditions for starting Story 10.1" already lists these as five distinct, independently-completable items. Split into 10.1A–10.1E below, preserving the original story number as a prefix. `Story 10.2`/`Story 10.3` numbering is unchanged. Nothing here changes the underlying `[REEWALUACJA]` findings above or the memo's `PROPOSED — REQUIRES USER ACCEPTANCE` status — this is a planning-structure fix, not a new decision.

## Story 10.1A — User decision gate: SECURITY DEFINER acceptance

status: blocked
depends_on: []

`[DECISION-GATE]` Not a coding task. Blocks 10.1B–10.1D. Requires the user to accept the `SECURITY DEFINER` recommendation in `EPIC-10-transaction-coordination-decision-memo.md` (or request further proof / an alternative mechanism). See `planning/capability-graph.json`'s `gate.EPIC-10.10_1.security-definer`.

Taski:
- [ ] **Obtain user acceptance (or an explicit alternative instruction) for the `SECURITY DEFINER` mechanism.**
      `verify:` none — this is a human decision, not a command. Do not mark done by proceeding anyway.

## Story 10.1B — Narrow ISO command-API design + DB role/functions + migration

status: blocked
depends_on: [Story 10.1A]

Opis: real narrow command-API surface for `iso-adapter` (one function per real domain-persistence operation, called only through a public port — packet §36), a real redesign pass of `PaymentService.submitPayment`/`Pain001PersistenceService.persist` to call that port, then `payment/V21`+ introducing `iso_adapter_role` + the real `SECURITY DEFINER` functions and revoking `sepa_app`'s grant on `iso.*`. This is the actual production-implementation work the two proof sessions cleared the path for — none of it has started.

Kryterium ukończenia: tylko rola `iso-adapter` pisze do `iso.*`, przez wąski command API, nie przez bezpośredni `JdbcTemplate`/`PaymentRepository`.

Taski:
- [ ] **Grant-test potwierdzający wyłączność zapisu `iso-adapter` do schematu `iso`.**
      `verify: ./mvnw -f backend test -Dtest=*IsoSchemaOwnershipTest*` — `NOT RUN`, `blocked` (patrz wyżej).

## Story 10.1C — `JSON_DIRECT` integration through the new command API

status: blocked
depends_on: [Story 10.1B]

Opis: `JsonDirectLineageRecorder.record` redirected through the new `iso-adapter` command API instead of writing `iso.*` directly inside `PaymentService`'s transaction.

Taski:
- [ ] **`JsonDirectLineageRecorder` calls the `iso-adapter` command API; no direct `iso.*` write remains in `payment-lifecycle`.**
      `verify: ./mvnw -f backend test -Dtest=*JsonDirectCommandApiIntegrationTest*` — `NOT RUN`, `blocked`.

## Story 10.1D — pain.001 integration through the new command API

status: blocked
depends_on: [Story 10.1B]

Opis: `Pain001LineageRecorder.record`/`Pain001PersistenceService.persist` redirected through the same command API.

Taski:
- [ ] **`Pain001PersistenceService` calls the `iso-adapter` command API; no direct `iso.*` write remains for the pain.001 channel.**
      `verify: ./mvnw -f backend test -Dtest=*Pain001CommandApiIntegrationTest*` — `NOT RUN`, `blocked`.

## Story 10.1E — Privilege/search_path/rollback/pool verification

status: **done**
depends_on: []

Opis: this is the part of the old Story 10.1 that is genuinely complete today, decoupled from the still-blocked production work above. Two full local, reversible proof suites exist as permanent test classes under `backend/src/test/java/com/sepanexus/epic10/` (never wired into production code): `SetLocalRoleSqlProofTest` (7/7), `SetLocalRoleJpaFlushProofTest` (2/2) — rejected mechanism, hazard found; `SecurityDefinerPrivilegeProofTest`, `SecurityDefinerTransactionProofTest`, `SecurityDefinerSearchPathProofTest` (real executed-and-defended CVE-2007-2138-class attack), `SecurityDefinerPoolIsolationProofTest`, `SecurityDefinerJpaFlushProofTest` (18/18) — recommended mechanism, no hazard found. Non-vacuousness confirmed via reverted deliberate mutations (packet §44, 3 of 6 required mutations executed live, a fourth proven constructionally, a fifth ruled not-applicable to a single-connection mechanism by construction).

Kryterium ukończenia: proof suites exist, pass, and are non-vacuous. Met.

Taski:
- [x] **`SET LOCAL ROLE` proof (rejected mechanism).**
      `verify: ./mvnw -f backend test -Dtest=SetLocalRoleSqlProofTest,SetLocalRoleJpaFlushProofTest` → `9/9 PASS` (2026-07-15).
- [x] **`SECURITY DEFINER` proof (recommended mechanism).**
      `verify: ./mvnw -f backend test -Dtest=SecurityDefinerPrivilegeProofTest,SecurityDefinerTransactionProofTest,SecurityDefinerSearchPathProofTest,SecurityDefinerPoolIsolationProofTest,SecurityDefinerJpaFlushProofTest` → `18/18 PASS` (2026-07-16).

## Story 10.2 — Korelacja pacs.002 przez lineage

status: not-started
depends_on: [Story 10.1B]

`[NARROWED 2026-07-16]`: was `[Story 10.1]` — repointed at `10.1B` specifically (the command-API + role/migration capability this story actually needs), consistent with the 10.1 split above.

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

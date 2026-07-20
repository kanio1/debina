---
status: done
depends_on: [EPIC-03-spring-modulith-backend-skeleton, EPIC-07-ci-cd-foundation]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-OWN-1, line 1257); sepa-nexus-blueprint-ownership-integration.md §9 (line 345); §3.6.4a/§3.6.5 layering+arch-test rules"
---

# EPIC-09 — Ownership: schema/grant enforcement mechanism (EPIC-OWN-1)

Ogólny mechanizm egzekwowania one-writer-per-schema — generyczne narzędzie, testowane od razu wobec modułów już istniejących (`payment` z Iteracji 0), rozszerzane w miarę powstawania kolejnych schematów. `[FREEZE]` reguła źródłowa: moduł pisze wyłącznie do własnego schematu.

`[PLANNING-DEFECT, ogólny dla całego epika]`: §3.6.4/§3.6.5 opisują pełną macierz ~16 modułów domenowych (`ingress`, `iso-adapter`, `signature`, `routing`, `settlement`, `ledger`, ...) z `CLAUDE.md`, ale w tej iteracji fizycznie istnieje tylko **jeden** rzeczywisty moduł domenowy (`payment-lifecycle`, zagnieżdżony w pakiecie `modules`) plus cienki `security`. Budowanie teraz pełnej macierzy `allowedDependencies`/ról DB dla modułów, które jeszcze nie istnieją w kodzie, byłoby projektowaniem architektury na wyrost — zabronione przez `CLAUDE.md` ("Nie projektuj nowych modułów ani architektury"). Każda story niżej implementuje więc **generyczny, w pełni działający mechanizm**, dowiedziony wobec tego, co istnieje dziś, z jawną notatką które wiersze macierzy czekają na przyszłe epiki ownership (EPIC-10…EPIC-18), gdy te moduły faktycznie powstaną.

## Story 9.1 — Jedna rola DB per moduł + granty/odbieranie uprawnień

status: done
depends_on: []

Opis: każdy schemat dostaje własną rolę-writer z jawnym grantem, bez domyślnego dostępu między schematami.

Kryterium ukończenia: rola modułu A nie może pisać do schematu modułu B.

Taski:
- [x] **Zbuduj generyczny wzorzec roli-writer per schemat** (na bazie `sepa_app`/`sepa_migration` z EPIC-01) i macierz grantów/odwołań uprawnień dla wszystkich obecnie istniejących schematów.
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=SchemaGrantMatrixTest` → `Tests run: 5, Failures: 0` — PASS (2026-07-14). Nowy `SchemaGrantMatrixTest` (`backend/src/test/java/com/sepanexus/payment/SchemaGrantMatrixTest.java`) generalizuje pre-istniejący `OutboxOwnershipTest` (który już dowodził tego samego wzorca wąsko, tylko dla `outbox_events`) na **wszystkie trzy** tabele schematu `payment` (`payments`, `outbox_events`, `inbox_events`): (1) `sepa_app` (prawdziwa rola właściciela) może pisać do wszystkich trzech — pozytywna kontrola, że wzorzec nie jest przypadkowo zbyt restrykcyjny; (2) syntetyczna `other_module_role` (rola stojąca za hipotetycznym drugim modułem — zgodnie z zamrożoną regułą `[DEFER]` "nie twórz wszystkich schematów z góry", nie tworzono prawdziwego drugiego schematu tylko po to, by to udowodnić) nie może zapisać do żadnej z trzech tabel (`SQLSTATE 42501`); (3) `other_module_role` nie ma nawet `USAGE` na schemacie `payment` (odczyt też odrzucony, `42501`).

## Story 9.2 — Flyway folder-per-module + granice pakietów repository

status: done
depends_on: [Story 9.1]

Opis: migracje Flyway w katalogu per moduł, repozytoria ograniczone do własnego pakietu.

Kryterium ukończenia: struktura katalogów Flyway odzwierciedla granice modułów.

Taski:
- [x] **Wymuś układ `backend/src/main/resources/db/migration/<module>/`** dla każdego nowego schematu i zasadę, że `Repository` żyje wyłącznie w pakiecie właściciela.
      `verify: find backend/src/main/resources/db/migration -mindepth 1 -maxdepth 1 -type d` → jeden katalog, `payment/` — PASS (2026-07-14). Przeniesiono `V2`…`V7` (`git mv`, historia zachowana) z płaskiego `db/migration/` do `db/migration/payment/`; `V1__roles.sql` pozostał w katalogu głównym — jest współdzieloną infrastrukturą (tworzy `sepa_app`/`sepa_migration` używane przez wszystkie przyszłe moduły), nie własnością jednego modułu. Flyway skanuje `classpath:db/migration`/`filesystem:src/main/resources/db/migration` **rekurencyjnie**, więc podkatalog nie wymagał zmiany konfiguracji — potwierdzone empirycznie: pełny regres (`./mvnw -f backend test` → `Tests run: 23/23` zanim doszły Story 9.3/9.4 testy) i restart `spring-boot:run` **przeciw już-zmigrowanej, długo działającej realnej bazie** (nie tylko świeżemu kontenerowi Testcontainers) → `{"status":"UP"}`, brak błędów walidacji checksum Flyway (Flyway waliduje po checksumie treści, nie po ścieżce pliku, więc przeniesienie było bezpieczne dla już zastosowanej historii migracji). Reguła "`Repository` żyje wyłącznie w pakiecie właściciela" jest już prawdziwa dziś (`PaymentRepository`/`OutboxEventRepository` w `modules.paymentlifecycle.repository`) i od teraz **egzekwowana** przez ArchUnit w Story 9.4, nie tylko odnotowana jako konwencja.

## Story 9.3 — Bramka Modulith `allowedDependencies`+`verify()`

status: done
depends_on: [EPIC-03-spring-modulith-backend-skeleton/Story 3.1]

Opis: rozszerzenie `ModularityTest` z EPIC-03 o pełną macierz dozwolonych/zabronionych zależności z §3.6.4.

Kryterium ukończenia: test pada na każdym zabronionym imporcie z macierzy §3.6.4.

`[PLANNING-DEFECT]`: pełna macierz §3.6.4 (16 modułów) nie może być skonfigurowana, bo 14 z tych modułów fizycznie nie istnieją jeszcze w kodzie (`ingress`, `iso-adapter`, `signature`, `routing`, `settlement`, `ledger`, `reconciliation`, `egress`, `reference-data`, `risk`, `simulation`, `reporting`, `case`, `evidence-audit`). Zaimplementowano to, co jest prawdziwe **dziś**: oba istniejące moduły (`modules` z zagnieżdżonym `payment-lifecycle`, oraz `security`) dostały jawne `@ApplicationModule(allowedDependencies = {})` — żaden nie potrzebuje dziś zależności od drugiego (potwierdzone: `grep` nie znalazł żadnego importu `com.sepanexus.security.*` w `modules/`, ani odwrotnie, poza frameworkowym `org.springframework.security.*`, co nie jest tym samym pakietem). Reszta macierzy (np. `ingress`→`iso-adapter`/`signature` dozwolone, `ingress`→`payment` zabronione) czeka na EPIC-10+, gdy te moduły faktycznie powstaną — dodawanie `allowedDependencies` dla nieistniejących pakietów już teraz byłoby igraniem z architekturą, nie jej egzekwowaniem.

Taski:
- [x] **Skonfiguruj `allowedDependencies`** dla wszystkich modułów wg macierzy §3.6.4 (zakres dziś: `modules` i `security`, oba `allowedDependencies = {}` — patrz `[PLANNING-DEFECT]` wyżej dla reszty macierzy).
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=ModularityTest` → PASS (2026-07-14). Dodano `backend/src/main/java/com/sepanexus/modules/package-info.java` i `.../security/package-info.java`, oba `@ApplicationModule(allowedDependencies = {})`. **Naruszenie zamierzone i potwierdzone**: `PaymentController` (moduł `modules`) tymczasowo wywołujący `new com.sepanexus.security.SecurityConfig()` (referencja do **głównego/API pakietu** `security`, nie do jego wewnętrznego podpakietu — to dowodzi, że `allowedDependencies={}` jest silniejsze niż samo domyślne domknięcie pakietów wewnętrznych z EPIC-07, bo blokuje też publiczne API innego modułu) → `BUILD FAILURE`: `Violations: Module 'modules' depends on module 'security' via ... PaymentController -> ... SecurityConfig. Allowed targets: none.` — **EXPECTED FAIL** potwierdzony. Eksperyment usunięty, `./mvnw -f backend test` → ponownie `BUILD SUCCESS`, `git status`/`git diff --check` czyste.

## Story 9.4 — Reguły ArchUnit dot. ownership

status: done
depends_on: [Story 9.3]

Opis: reguły ArchUnit z §3.6.4a/§3.6.5 — brak repository poza pakietem właściciela, brak `settlement`→`ledger` repository, brak `Instant.now()` poza `ClockPort`, pakiet GraphQL nie zależy od command services, brak `@TenantId`/filtru tenant na poziomie Hibernate, brak `Repository` w klasie Controller.

Kryterium ukończenia: wszystkie reguły ArchUnit z §3.6.5 wdrożone i przechodzą.

`[PLANNING-DEFECT]`: nie wszystkie reguły §3.6.5 są dziś sprawdzalne bez wymyślania nieistniejącej architektury: `settlement`→`ledger` (moduł `settlement`/`ledger` nie istnieją), `Instant.now()` poza `ClockPort` (`ClockPort` nie istnieje — obecny kod w ogóle nie woła `Instant.now()`/`OffsetDateTime.now()` poza jednym miejscu w `PaymentEntity`, bez portu do owinięcia), pakiet GraphQL (GraphQL to `[MVP]` ale nie zbudowany w Iteracji 0 — `[FREEZE]` "GraphQL read-only w MVP" nie znaczy "GraphQL zbudowany w Iteracji 0"), `signature`-owe reguły (moduł `signature` to EPIC-31, `not-started`), reguła wyścigu dwóch `payment_approver` (funkcja aprobaty to Iteracja 1+, `payment_approvals` nie istnieje). Zaimplementowano trzy reguły, które SĄ dziś sprawdzalne wobec realnego kodu, bez wynajdywania architektury: brak repository poza pakietem właściciela, brak referencji do `Repository` w klasie Controller, brak `@TenantId`/`@Filter`/`@FilterDef` Hibernate gdziekolwiek w modelu encji.

Taski:
- [x] **Zaimplementuj pakiet testów ArchUnit** dokładnie wg listy w §3.6.5 (ownership blueprint) — jedna reguła = jeden test, z nazwą odzwierciedlającą regułę źródłową (zakres dziś: patrz `[PLANNING-DEFECT]` wyżej).
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=OwnershipArchRulesTest` → `Tests run: 3, Failures: 0` — PASS (2026-07-14). Nowy `backend/src/test/java/com/sepanexus/OwnershipArchRulesTest.java`, trzy reguły ArchUnit (core, bez dodatkowej zależności `archunit-junit5` — importowana transitywnie przez `spring-modulith-starter-test`): `repositoriesLiveOnlyInARepositoryPackage`, `controllersNeverReferenceARepositoryType`, `noHibernateTenantIdOrTenantFilterAnywhereInTheEntityModel`. **Wszystkie trzy zweryfikowane jako nie-próżne** (non-vacuous) przez tymczasowy deliberate-violation dla każdej: (1) `MisplacedRepository` poza pakietem `.repository` → `BUILD FAILURE` (EXPECTED FAIL), usunięty; (2) pole typu `PaymentRepository` dodane do `PaymentController` → `BUILD FAILURE` (EXPECTED FAIL), usunięte; (3) tymczasowa klasa z polem `@org.hibernate.annotations.TenantId` → `BUILD FAILURE` (EXPECTED FAIL), usunięta. Po każdym eksperymencie: usunięcie plików, `./mvnw -f backend test` → `BUILD SUCCESS`. Finalny pełny regres: `Tests run: 26, Failures: 0, Errors: 0` — PASS (2026-07-14). `git status --short`/`git diff --check` czyste (tylko zamierzone nowe/zmienione pliki tej sesji).

## Story 9.5 — Runtime datasource ownership boundary

status: done
depends_on: [Story 9.1, EPIC-18-per-schema-outbox-inbox-rollout/Story 18.5]

Opis: runtime rozdziela domenową tożsamość writer (`sepa_app`) od technicznej tożsamości relay (`outbox_dispatcher_role`). Dispatcher może używać tylko jawnie kwalifikowanych beanów relay; usługi domenowe i repozytoria nie mogą przez przypadek przejąć infrastruktury relay.

Kryterium ukończenia: test Spring wiring dowodzi osobnych datasource i transaction managerów; relay nie zależy od JPA/repository, a domain path nie używa relay role.

**[DONE 2026-07-20]** `OutboxRelayRuntimeWiringTest` proves Spring wires distinct
`sepa_app`/`outbox_dispatcher_role` datasource identities and transaction managers, injects the
qualified relay JDBC template into both payment and ISO dispatchers, publishes both outbox types,
and rejects a relay-role domain write. `RuntimeDatasourceOwnershipTest` structurally rejects
repository/entity-manager use, exercises non-vacuous forbidden-domain fixtures, and confirms the
scheduler delegates only to those transaction-qualified dispatchers. Story 7.4's PostgreSQL
18/Kafka scheduled runtime proof executes that route under the restricted role. No domain service
or repository gains access to relay infrastructure.

Taski:
- [x] **Dodaj structural i Spring-wiring proof granicy datasource** dla payment oraz ISO relay, wraz z niepustymi fixture’ami zabronionych zależności.
      `verify: ./mvnw -f backend test -Dtest=OutboxRelayRuntimeWiringTest,RuntimeDatasourceOwnershipTest` → `4/0/0 PASS` (2026-07-20).
- [x] **Sprawdź, że scheduler relay używa relay transaction managera**, po wdrożeniu Story 7.4.
      `verify: ./mvnw -f backend test -Dtest=ScheduledRelayOperationalRuntimeTest,RuntimeDatasourceOwnershipTest` → `4/0/0 PASS` (2026-07-20; actual restricted-role scheduled route).

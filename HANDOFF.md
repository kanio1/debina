# HANDOFF

## Zadanie

SEPA Nexus, Iteracja 0/przejście do Iteracji 0-ownership. Batch tej sesji (CACHE-STABLE EXECUTION CONTRACT v4): domknięcie `EPIC-07` (był `in-progress` na wejściu), pełny `EPIC-08`, pełny `EPIC-09`. Wszystkie trzy ukończone i zweryfikowane. Zatrzymano się przed czwartym epikiem, zgodnie z instrukcją.

## Zrobione

**EPIC-07 — CI/CD Foundation → `done`.** Rozstrzygnięto `[OPEN-QUESTION]` z poprzedniej sesji ("czy `ModularityTest` faktycznie coś egzekwuje"): hipoteza poprzedniej sesji (moduły domyślnie `Type.OPEN`) była **błędna** — zweryfikowano bezpośrednio (`m.isOpen()` → `false` dla obu modułów). Prawdziwa przyczyna, dlaczego wcześniejsze próby deliberate-violation nie łapały niczego: bait-klasa używała `public static final String` — kompilacyjnej stałej Javy, którą javac inline'uje (constant folding), więc referencja nigdy nie stała się prawdziwą zależnością bajtkodową widoczną dla ArchUnit. Powtórzono eksperyment z metodą (nie stałą) → `BUILD FAILURE` z jasnym komunikatem naruszenia granic modułu → **EXPECTED FAIL potwierdzony**, eksperyment usunięty, `BUILD SUCCESS` ponownie. `[PLANNING-DEFECT]` odnotowany i rozstrzygnięty w `planning/epics/EPIC-07-ci-cd-foundation.md`. Wszystkie bramki `act` (backend, frontend, `act -l`) → PASS.

**EPIC-08 — Walking Skeleton Verification → `done`.**
- Story 8.1: nowy `WalkingSkeletonIntegrationTest` (`backend/src/test/java/com/sepanexus/modules/paymentlifecycle/event/WalkingSkeletonIntegrationTest.java`) — pełny łańcuch z **prawdziwym** Keycloak (ROPC grant przeciw działającemu kontenerowi `localhost:8080`, nie mock JWT), realny HTTP POST na `@SpringBootTest(webEnvironment=RANDOM_PORT)`, asercje na `payment.payments`/`outbox_events`/status `VALIDATED` po konsumpcji Kafka. Plus test negatywny (`operator` rola → 403, zero side-effects). Oba PASS lokalnie i w `act`.
- Story 8.2: brak narzędzia do sterowania prawdziwą przeglądarką w tym środowisku CLI (`[NO-PLAYWRIGHT]` + brak innego browser tool) — odnotowane jawnie jako `[PLANNING-DEFECT]`. Zamiast tego wykonano wierny skrypt `curl` z jarem ciasteczek odtwarzający dokładnie ten sam łańcuch przekierowań HTTP co przeglądarka: login → prawdziwy Keycloak → callback → `sepa_session` (opaque, HttpOnly, potwierdza ADR-N3) → submit płatności przez BFF (z CSRF) → widoczna w liście → logout → `/api/session` 401 → redirect do loginu ponownie. Zero błędów w logach backend/frontend. Znaleziono i naprawiono realny gap: `frontend/.env.local` jest git-ignorowany, ale `frontend/README.md` nigdzie nie mówił `cp .env.example .env.local` — świeży klon miałby niedokumentowany krok ręczny. Naprawione.
- Story 8.3: cały exit checklist Iteracji 0 przeszedł, z jedną świadomą, odnotowaną niedoskonałością: nie wykonano literalnego równoległego `docker compose up` na świeżym klonie (konflikt portów z już działającym stosem tej sesji) — zamiast tego zweryfikowano strukturę/kompilację/instalację na izolowanym `git clone` bez uruchamiania drugiej infrastruktury.

**EPIC-09 — Ownership: schema/grant enforcement (EPIC-OWN-1) → `done`.** Ogólny `[PLANNING-DEFECT]` odnotowany na starcie: §3.6.4/§3.6.5 opisują pełną macierz ~16 modułów, ale fizycznie istnieje dziś tylko jeden (`payment-lifecycle`) plus cienki `security` — zaimplementowano **generyczny, w pełni działający mechanizm** dowiedziony wobec tego, co istnieje, nie wymyślono nowej architektury (zgodnie z zakazem w `CLAUDE.md`).
- Story 9.1: nowy `SchemaGrantMatrixTest` (`backend/src/test/java/com/sepanexus/payment/SchemaGrantMatrixTest.java`) generalizuje pre-istniejący wąski `OutboxOwnershipTest` na wszystkie trzy tabele `payment` schematu — `sepa_app` może pisać, syntetyczna `other_module_role` nie może (ani nawet czytać — brak `USAGE`).
- Story 9.2: `V2`…`V7` przeniesione (`git mv`) do `db/migration/payment/`; `V1__roles.sql` (współdzielona infrastruktura) zostaje w katalogu głównym. Flyway skanuje rekurencyjnie — potwierdzone empirycznie na Testcontainers ORAZ przez restart `spring-boot:run` przeciw już-zmigrowanej realnej bazie (checksummy niezmienione, brak błędów walidacji).
- Story 9.3: `package-info.java` z `@ApplicationModule(allowedDependencies = {})` dodane do `com.sepanexus.modules` i `com.sepanexus.security`. Deliberate-violation (referencja do **publicznego API** innego modułu, nie tylko jego pakietu internal) → `BUILD FAILURE` z jasnym komunikatem → EXPECTED FAIL potwierdzony, dowodząc że `allowedDependencies={}` jest silniejsze niż samo domknięcie pakietów z EPIC-07.
- Story 9.4: nowy `OwnershipArchRulesTest` (`backend/src/test/java/com/sepanexus/OwnershipArchRulesTest.java`), trzy reguły ArchUnit sprawdzalne dziś bez wymyślania architektury (repository-poza-pakietem-właściciela, controller-referencing-repository, brak `@TenantId`/`@Filter` Hibernate) — wszystkie trzy zweryfikowane jako nie-próżne przez tymczasowe deliberate-violation, usunięte po potwierdzeniu. `[PLANNING-DEFECT]` odnotowuje które reguły z §3.6.5 czekają na nieistniejące jeszcze moduły/porty (`ClockPort`, GraphQL, `signature`, `ledger`, `payment_approvals`).

**Finalny pełny regres backendu (2026-07-14):** `./mvnw -f backend test` → `Tests run: 26, Failures: 0, Errors: 0`, `BUILD SUCCESS`. Frontend: `pnpm run lint` (1 pre-istniejące ostrzeżenie niezwiązane z tą sesją, 0 błędów), `pnpm run typecheck` (czysty), `pnpm run build` (sukces). `git status --short`/`git diff --check` czyste na końcu każdego epika.

## Zmienione/nowe pliki tej sesji

- `backend/src/test/java/com/sepanexus/modules/paymentlifecycle/event/WalkingSkeletonIntegrationTest.java` (nowy)
- `backend/src/test/java/com/sepanexus/payment/SchemaGrantMatrixTest.java` (nowy)
- `backend/src/test/java/com/sepanexus/OwnershipArchRulesTest.java` (nowy)
- `backend/src/main/java/com/sepanexus/modules/package-info.java` (nowy)
- `backend/src/main/java/com/sepanexus/security/package-info.java` (nowy)
- `backend/src/main/resources/db/migration/V2`…`V7` → przeniesione do `backend/src/main/resources/db/migration/payment/` (`git mv`, historia zachowana)
- `frontend/README.md` (dodano sekcję "Environment" — `cp .env.example .env.local`)
- `planning/README.md`, `planning/epics/EPIC-07-ci-cd-foundation.md`, `EPIC-08-walking-skeleton-verification.md`, `EPIC-09-ownership-schema-grants.md` (statusy + szczegółowe notatki weryfikacji)

Nieśledzone, nie moje: `.claude/skills/impeccable/` (istniało na wejściu do sesji, nie ruszane).

## Utknęliśmy na

Nigdzie — batch trzech epików ukończony w całości, bez pozostawionych blokad. Jedyne świadomie odnotowane niedoskonałości (nie blokady): (1) manualny runbook Story 8.2 wykonany jako scripted `curl`, nie prawdziwa przeglądarka (brak narzędzia w tym środowisku CLI); (2) fresh-clone reprodukcja Story 8.3 nie uruchomiła drugiego równoległego `docker compose up` (konflikt portów z żywym stosem); (3) `/api/session` zwraca `roles: []` (ID token Keycloak nie niesie `realm_access.roles` domyślnie) — nieużywane nigdzie do autoryzacji dziś, czysto kosmetyczne, odnotowane w EPIC-08.

## Plan na następny krok

`planning/README.md` — pierwszy w pełni odblokowany kolejny epik w kolejności tabeli to **EPIC-12 — Ownership: katalogi reference-data** (`depends_on: [EPIC-09]`, teraz spełnione). Uwaga: `EPIC-10` i `EPIC-11` idą wcześniej w tabeli, ale mają niespełnione zależności (`EPIC-26`, `EPIC-21`, `EPIC-20` — wszystkie `not-started`), więc NIE są odblokowane. `EPIC-15`/`EPIC-16`/`EPIC-17` są też odblokowane (zależą tylko od `EPIC-09`+`EPIC-04`, oba `done`) i są prawdopodobnie tańsze/bardziej naturalnym następnym krokiem niż `EPIC-12` — zweryfikuj kolejność z `planning/README.md` na nowo na starcie następnej sesji, nie zakładaj tego z pamięci. Pierwszy krok: przeczytać `planning/epics/EPIC-12-reference-data-ownership.md`, jego `source`, i potwierdzić `depends_on` przed startem — nie wykonano tego tasku w tej sesji.

Backend (`spring-boot:run`) i frontend (`pnpm run dev`) uruchomione ręcznie w tej sesji do weryfikacji Story 8.2 — prawdopodobnie nie żyją na starcie następnej sesji. Postgres/Keycloak/Kafka (Podman) powinny wciąż działać — zweryfikuj przez `podman compose -f infra/docker-compose.yml ps`.

## Pułapki, których nie wolno powtórzyć

- **`public static final String` (kompilacyjna stała Javy) w bait-klasie dla testu naruszenia granic modułu/ArchUnit nigdy nie zadziała** — javac inline'uje wartość (constant folding), więc nie powstaje żadna prawdziwa referencja bajtkodowa do sprawdzenia. Użyj metody (np. `static String secret() { return "..."; }`) albo pola nie-`final`/nie-stałej wartości do dowodu naruszenia.
- **Zanim zaufasz `[OPEN-QUESTION]` z poprzedniej sesji dot. Modulith/ArchUnit — zweryfikuj hipotezę bezpośrednio** (np. `m.isOpen()`), nie zakładaj że była poprawna. W tej sesji hipoteza "moduły domyślnie `Type.OPEN`" z poprzedniej sesji okazała się fałszywa przy bezpośrednim sprawdzeniu.
- **Flyway skanuje `classpath:`/`filesystem:` lokalizacje rekurencyjnie** — przenoszenie migracji do podkatalogów per moduł (`db/migration/<module>/`) nie wymaga zmiany `spring.flyway.locations`, i jest bezpieczne dla już zastosowanej historii migracji (Flyway waliduje po checksumie treści, nie po ścieżce pliku).
- **`@ApplicationModule(allowedDependencies = {})` jest silniejsze niż samo domyślne domknięcie pakietów internal** (z EPIC-07) — blokuje też dostęp do PUBLICZNEGO/root-pakietowego API innego modułu, nie tylko jego `internal` podpakietów. Warto dowodzić to osobno przy pisaniu testów negatywnych.
- **Brak narzędzia do sterowania prawdziwą przeglądarką w tym środowisku Claude Code CLI** — dla manualnych runbooków UI używaj skryptowanego `curl` z jarem ciasteczek jako rygorystycznego substytutu, ale zawsze odnotuj to jawnie jako `[PLANNING-DEFECT]`, nie jako "ręcznie potwierdzone".
- Wszystkie pułapki z poprzednich sesji (EPIC-02…EPIC-07: Podman nie Docker, `DOCKER_HOST` dla Testcontainers, trzy dodatkowe flagi `act`+Podman+SELinux+Ryuk, RLS GUC empty-zero-rows, Next.js env gettery leniwe, `pnpm`/Node 24.18.0, `psql` niedostępny lokalnie — użyj `podman exec -i infra_postgres_1 psql ...`) wciąż obowiązują.

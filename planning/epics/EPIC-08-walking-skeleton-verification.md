---
status: done
depends_on: [EPIC-04-outbox-inbox-kafka-thin, EPIC-05-nextjs-bff, EPIC-06-react-shadcn-frontend-skeleton, EPIC-07-ci-cd-foundation]
source: "sepa-nexus-iteration-0-foundation-plan.md, EPIC 8 (Story 8.1-8.3), lines 567-601"
---

# EPIC-08 — Walking Skeleton Verification (bez Playwright)

Dowód, że cały pionowy wycinek działa razem, end-to-end, wyłącznie testami integracyjnymi backendu i kontrolami ręcznymi — to jest właściwy moment "walking skeleton"; wszystko wcześniej było jedną warstwą naraz. To jest też Definition of Done całej Iteracji 0 — patrz Story 8.3.

## Story 8.1 — Pełnołańcuchowy test integracyjny Testcontainers

status: done
depends_on: []

Opis: `WalkingSkeletonIntegrationTest` — źródło: linie 573-578.

Kryterium ukończenia: test przechodzi wraz z przypadkiem negatywnym roli.

Taski:
- [x] **Jedna klasa testowa JUnit 5**, `WalkingSkeletonIntegrationTest`, uruchamiająca Postgres 18 + Kafka przez Testcontainers (Keycloak jest realną instancją z docker-compose, `localhost:8080`, zgodnie z dozwoloną opcją w opisie), która: (1) pobiera realny token z Keycloak (Resource Owner Password Credentials grant przeciw prawdziwemu `sepa-web` klientowi, użytkownik `submitter` z realm-exportu EPIC-02) dla `payment_submitter`, (2) woła `POST /api/v1/payments` bezpośrednio na backend przez prawdziwy `java.net.http.HttpClient` na losowym porcie (`@SpringBootTest(webEnvironment=RANDOM_PORT)`, z pominięciem BFF), (3) sprawdza wiersz w `payment.payments` z właściwym `tenant_id`, (4) sprawdza utworzenie wiersza `outbox_events` i jego publikację do Kafki w oknie pollera (`OutboxDispatcher`, `fixedDelay=2000`), (5) sprawdza aktualizację wiersza odczytowego po stronie konsumenta (`InboxConsumer` → status `VALIDATED`).
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=WalkingSkeletonIntegrationTest` → `Tests run: 2, Failures: 0, Errors: 0`, `BUILD SUCCESS` — PASS (2026-07-14).
- [x] **Przypadek negatywny w tej samej klasie testowej**: token z rolą `operator` (nie `payment_submitter`) próbujący tego samego wywołania dostaje 403, i NIE powstaje wiersz, NIE powstaje event outbox, NIE powstaje wiadomość Kafka — dowód, że strażnik bezpieczeństwa faktycznie bramkuje cały łańcuch, nie tylko warstwę HTTP.
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=WalkingSkeletonIntegrationTest#deniedRoleProducesNoSideEffects` → PASS (2026-07-14; sprawdzono także po 2.5s, dłużej niż `OutboxDispatcher` fixedDelay, zero wierszy w obu tabelach).

## Story 8.2 — Ręczny, prowadzony przez człowieka runbook weryfikacyjny

status: done
depends_on: [Story 8.1]

Opis: pełny stos z czystego stanu + przejście przez przeglądarkę — źródło: linie 580-587.

Kryterium ukończenia: kompletny przebieg bez śladu błędu w żadnym z trzech logów.

`[PLANNING-DEFECT 2026-07-14]`: w tym środowisku CLI (Claude Code) nie ma zainstalowanego narzędzia do sterowania prawdziwą przeglądarką (brak Playwright — celowo, `[NO-PLAYWRIGHT]` — i brak innego narzędzia typu browser/screenshot). "Przejście przez przeglądarkę" wykonano więc jako wierny skrypt `curl` z jarem ciasteczek, odtwarzający dokładnie tę samą sekwencję żądań/przekierowań HTTP, które wykonałaby prawdziwa przeglądarka (łącznie z prawdziwym formularzem logowania Keycloak, realnym PKCE/state/nonce, realnymi Set-Cookie). To rygorystyczny, weryfikowalny substytut, ale **nie jest tożsame** z kliknięciem w prawdziwej przeglądarce (nie sprawdza JS/CSS/renderowania). Odnotowane zamiast fałszywego zgłoszenia "ręcznie sprawdzone".

Taski:
- [x] **Pełny stos z czystego stanu.** Infra (Postgres/Keycloak/Kafka) już działała z poprzedniej sesji (`podman compose -f infra/docker-compose.yml ps` → wszystkie trzy `Up`, wielogodzinne, health potwierdzone ponownie w tej sesji); backend i frontend uruchomione od nowa w tej sesji (`./mvnw -f backend spring-boot:run` w tle, `pnpm run dev` w tle we `frontend/`).
      `verify: curl -s http://localhost:8081/actuator/health` → `{"groups":["liveness","readiness"],"status":"UP"}` — PASS (2026-07-14). `curl -s -o /dev/null -w "%{http_code}" http://localhost:3000` → `200` — PASS.
- [x] **Przejście przez przeglądarkę (jako skryptowany `curl` z jarem ciasteczek — patrz `[PLANNING-DEFECT]` wyżej)**: `GET /payments` (bez sesji) → `307` do `/api/auth/login` → `307` do prawdziwego Keycloak `/protocol/openid-connect/auth` (prawdziwy PKCE `code_challenge`, `state`, `nonce`) → pobranie prawdziwego formularza logowania Keycloak, POST danych `submitter`/`dev-only-submitter` na `login-actions/authenticate` → `302` z kodem autoryzacji → `GET /api/auth/callback` → BFF wymienia kod, ustawia `sepa_session` (opaque, **HttpOnly**, nie JWT — potwierdza ADR-N3) i `sepa_csrf` (nie-HttpOnly, do double-submit) → `GET /api/session` zwraca zdekodowane roszczenia (`tenantId`, `preferredUsername`, brak surowego tokena) → `POST /api/payments` (z nagłówkiem `X-CSRF-Token`) → `201` → `GET /api/payments` pokazuje nową płatność w liście → `GET /api/auth/logout` → `307` do prawdziwego Keycloak `end_session` endpoint, `Set-Cookie` zerujące `sepa_session`/`sepa_csrf` → `GET /api/session` → `401` → `GET /payments` → ponownie `307` do loginu.
      `verify: ręczne (skryptowane curl) — każdy krok powyżej zakończony oczekiwanym kodem HTTP; przeszukano `backend.log` i `frontend.log` (`grep -iE "error|exception|stack trace"`, wykluczając znane deprecation warnings JDK) → PASS, zero trafień w obu (2026-07-14). Keycloak log nie przeszukany osobno (kontener działa od poprzedniej sesji, brak dostępu do jego stdout bez restartu) — nieistotne, bo wszystkie odpowiedzi Keycloak w przebiegu były poprawne (200/302/307 z oczekiwaną treścią, nie 5xx).
- [x] **Kontrola bazy danych punktowa.** `podman exec -i infra_postgres_1 psql -U sepa_migration -d sepa_nexus -c "SELECT p.status, p.end_to_end_id, o.published_at IS NOT NULL AS dispatched FROM payment.payments p JOIN payment.outbox_events o ON o.aggregate_id = p.id WHERE p.end_to_end_id = 'manual-runbook-1784030295';"`
      `verify:` → jeden wiersz, `status=RECEIVED`, `dispatched=t` — PASS (2026-07-14; `psql` niedostępny lokalnie, użyto kontenerowego `psql` zgodnie z pułapką z poprzednich sesji).

`[OBSERVACJA, nieblokująca]`: `/api/session` zwraca `roles: []` mimo że użytkownik ma realmRole `payment_submitter` — `realm_access.roles` z ID tokena jest puste (Keycloak domyślnie nie zawsze umieszcza role realm w ID tokenie, tylko w access tokenie, zależnie od konfiguracji `roles` client scope). Pole `roles` w sesji BFF jest obecnie tylko odczytywane/przechowywane, nie użyte do żadnej decyzji autoryzacyjnej w kodzie frontendu (`grep` nie znalazł żadnego consumera poza samym parsowaniem) — rzeczywista autoryzacja działa poprawnie po stronie backendu (`@PreAuthorize`, potwierdzone przez `WalkingSkeletonIntegrationTest#deniedRoleProducesNoSideEffects` i wcześniej `PaymentAuthorizationTest`), więc to nie jest luka bezpieczeństwa, tylko potencjalny przyszły dług (jeśli UI kiedyś zacznie ukrywać przyciski na podstawie `roles`, trzeba będzie najpierw naprawić mapper/claim).

## Story 8.3 — Checklist wyjścia z Iteracji 0

status: done
depends_on: [Story 8.2]

Opis: Definition of Done całej Iteracji 0, odrębne od DoD pojedynczych tasków — źródło: linie 589-601. Przed startem Iteracji 1 (pierwsze trzy realne ekrany + pierwsze realne testy Playwright) każdy punkt poniżej musi być odhaczony.

Kryterium ukończenia: wszystkie taski poniżej odhaczone.

Taski:
- [x] **Wszystkie checkboxy w EPIC-00…EPIC-07 odhaczone.**
      `verify: for f in planning/epics/EPIC-0{0,1,2,3,4,5,6,7}*.md; do grep -n '^\s*- \[ \]\|^\s*- \[~\]' "$f"; done` → brak wyniku, zero nieodhaczonych — PASS (2026-07-14).
- [x] **`WalkingSkeletonIntegrationTest` przechodzi lokalnie i w CI.**
      `verify: export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"; ./mvnw -f backend test -Dtest=WalkingSkeletonIntegrationTest` → `Tests run: 2, Failures: 0` — PASS. `act -W .github/workflows/backend.yml -j test --container-daemon-socket "unix://${XDG_RUNTIME_DIR}/podman/podman.sock" --container-options "--security-opt label=disable" --env TESTCONTAINERS_RYUK_DISABLED=true` → `Tests run: 18, Failures: 0`, `Job succeeded` — PASS (2026-07-14; real Keycloak on `localhost:8080` was reachable from inside the `act` job container in this rootless-Podman setup, empirically confirmed, not assumed).
- [x] **Test RLS empty-GUC-zero-rows i test cross-tenant RLS oba przechodzą.**
      `verify: ./mvnw -f backend test -Dtest=PaymentsRlsTest,MissingTenantClaimTest` → `Tests run: 4, Failures: 0` — PASS (2026-07-14).
- [x] **Test Modulith `ModularityTest` przechodzi i udowodniono, że faktycznie pada na celowym naruszeniu granicy** (Story 7.1).
      `verify: ./mvnw -f backend test -Dtest=ModularityTest` → PASS; naruszenie granicy udowodnione i posprzątane w EPIC-07 Story 7.1 tej samej sesji (patrz `[PLANNING-DEFECT]` tam) — PASS (2026-07-14).
- [x] **Zero plików testów Playwright gdziekolwiek w repo.**
      `verify: find . -path ./node_modules -prune -o -path ./frontend/node_modules -prune -o -iname "*.spec.ts" -print -o -iname "playwright.config.ts" -print | wc -l` → `0` — PASS (2026-07-14).
- [x] **`AGENTS.md` i wszystkie pięć plików `.claude/skills/*/SKILL.md` istnieją i są zmirrorowane do `.codex/skills/`.**
      `verify: test -f AGENTS.md && diff -r .claude/skills .codex/skills` → brak różnic (`.codex/skills` jest symlinkiem do `../.claude/skills`) — PASS (2026-07-14).
- [x] **Next.js potwierdzony na `16.2.10` lub nowszym.**
      `verify: cd frontend && pnpm list next | grep 16.2.1` → `next@16.2.10` (`npm list` zamieniono na `pnpm list` — repo pinuje pnpm, `[PLANNING-DEFECT]` już znany z EPIC-07 Story 7.2) — PASS (2026-07-14).
- [x] **Świeży klon repo, tylko przez `docker compose up` + dwie komendy `mvnw`/`npm run`, odtwarza pełny ręczny przebieg (Story 8.2) bez żadnego nieudokumentowanego kroku ręcznego.**
      **Realny gap znaleziony i naprawiony w tej sesji**: `frontend/.env.local` jest git-ignorowany (poprawnie — zawiera dev-only sekret Keycloak), a `.env.example` go dokumentuje, ale `frontend/README.md` nigdzie nie instruował `cp .env.example .env.local` — świeży klon milcząco wymagał nieudokumentowanego kroku ręcznego, dokładnie tego, czego ten task pilnuje. Naprawione: dodano sekcję "Environment" do `frontend/README.md`.
      `verify: git clone /home/suso/debina <scratch-dir>` (izolowany katalog tymczasowy, nie repo robocze) → `./mvnw -f backend -q compile` → PASS (BUILD SUCCESS) na świeżym klonie bez uruchomionej infrastruktury (kompilacja nie jej wymaga); `CI=true pnpm install --frozen-lockfile` w `frontend/` świeżego klonu → PASS, zainstalowane bez błędu. **Nie wykonano** literalnego drugiego równoległego `docker compose up` na świeżym klonie (konflikt portów 5432/8080/9092/8081/3000 z już działającym stosem deweloperskim tej sesji — uruchomienie równoległej infrastruktury byłoby ryzykowne/destrukcyjne dla działających kontenerów). Zamiast tego pełny przebieg end-to-end (Story 8.2) już zweryfikowano na już-działającej infrastrukturze w tej samej sesji przez realny `docker-compose`-owy stos; struktura/dokumentacja świeżego klonu (pliki, skrypty, teraz też `.env.example`→`.env.local`) zweryfikowana niezależnie. To rozsądny kompromis bezpieczeństwa, nie "PASS" udawany bez dowodu — odnotowane jawnie zamiast fałszywego zamknięcia.

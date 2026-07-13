---
status: not-started
depends_on: [EPIC-04-outbox-inbox-kafka-thin, EPIC-05-nextjs-bff, EPIC-06-react-shadcn-frontend-skeleton, EPIC-07-ci-cd-foundation]
source: "sepa-nexus-iteration-0-foundation-plan.md, EPIC 8 (Story 8.1-8.3), lines 567-601"
---

# EPIC-08 — Walking Skeleton Verification (bez Playwright)

Dowód, że cały pionowy wycinek działa razem, end-to-end, wyłącznie testami integracyjnymi backendu i kontrolami ręcznymi — to jest właściwy moment "walking skeleton"; wszystko wcześniej było jedną warstwą naraz. To jest też Definition of Done całej Iteracji 0 — patrz Story 8.3.

## Story 8.1 — Pełnołańcuchowy test integracyjny Testcontainers

status: not-started
depends_on: []

Opis: `WalkingSkeletonIntegrationTest` — źródło: linie 573-578.

Kryterium ukończenia: test przechodzi wraz z przypadkiem negatywnym roli.

Taski:
- [ ] **Jedna klasa testowa JUnit 5**, `WalkingSkeletonIntegrationTest`, uruchamiająca Postgres 18 + Kafka przez Testcontainers (Keycloak może być realną instancją z docker-compose albo obrazem Testcontainers), która: (1) pobiera realny token z Keycloak dla `payment_submitter`, (2) woła `POST /api/v1/payments` bezpośrednio na backend (z pominięciem BFF — ten test dowodzi łańcucha backendu, nie UI BFF), (3) sprawdza wiersz w `payment.payments` z właściwym `tenant_id`, (4) sprawdza utworzenie wiersza `outbox_events` i jego publikację do Kafki w oknie pollera, (5) sprawdza aktualizację wiersza odczytowego po stronie konsumenta.
      `verify: ./mvnw -f backend test -Dtest=WalkingSkeletonIntegrationTest` → przechodzi, log pokazuje wszystkie pięć asercji trafionych po kolei.
- [ ] **Przypadek negatywny w tej samej klasie testowej**: token z rolą `operator` (nie `payment_submitter`) próbujący tego samego wywołania dostaje 403, i NIE powstaje wiersz, NIE powstaje event outbox, NIE powstaje wiadomość Kafka — dowód, że strażnik bezpieczeństwa faktycznie bramkuje cały łańcuch, nie tylko warstwę HTTP.
      `verify: ./mvnw -f backend test -Dtest=WalkingSkeletonIntegrationTest#deniedRoleProducesNoSideEffects` → przechodzi.

## Story 8.2 — Ręczny, prowadzony przez człowieka runbook weryfikacyjny

status: not-started
depends_on: [Story 8.1]

Opis: pełny stos z czystego stanu + przejście przez przeglądarkę — źródło: linie 580-587.

Kryterium ukończenia: kompletny przebieg bez śladu błędu w żadnym z trzech logów.

Taski:
- [ ] **Pełny stos z czystego stanu.** `docker compose -f infra/docker-compose.yml up -d && ./mvnw -f backend spring-boot:run & cd frontend && npm run dev`
      `verify: wszystkie trzy kontenery infra healthy, backend odpowiada na :8081/actuator/health, frontend odpowiada na :3000.`
- [ ] **Przejście przez przeglądarkę**: otwórz `http://localhost:3000`, zaloguj się jako `payment_submitter` (realne przekierowanie Keycloak, nie zaślepka), prześlij jedną płatność, zobacz ją w tabeli, wyloguj się, potwierdź że `/api/session` zwraca 401 po wylogowaniu.
      `verify: ręczne — każdy krok powyżej kończy się bez błędu konsoli lub stack trace w którymkolwiek z trzech logów (backend, frontend, Keycloak).`
- [ ] **Kontrola bazy danych punktowa.** `psql -c "SELECT p.status, o.published_at IS NOT NULL AS dispatched FROM payment.payments p JOIN payment.outbox_events o ON o.aggregate_id = p.id"`
      `verify: jeden wiersz, status RECEIVED, dispatched = t.`

## Story 8.3 — Checklist wyjścia z Iteracji 0

status: not-started
depends_on: [Story 8.2]

Opis: Definition of Done całej Iteracji 0, odrębne od DoD pojedynczych tasków — źródło: linie 589-601. Przed startem Iteracji 1 (pierwsze trzy realne ekrany + pierwsze realne testy Playwright) każdy punkt poniżej musi być odhaczony.

Kryterium ukończenia: wszystkie taski poniżej odhaczone.

Taski:
- [ ] **Wszystkie checkboxy w EPIC-00…EPIC-07 odhaczone.**
      `verify: przegląd wszystkich plików EPIC-00…EPIC-07 w /planning/epics/ — brak nieodhaczonych taskbów.`
- [ ] **`WalkingSkeletonIntegrationTest` przechodzi lokalnie i w CI.**
      `verify: ./mvnw -f backend test -Dtest=WalkingSkeletonIntegrationTest && act -W .github/workflows/backend.yml`
- [ ] **Test RLS empty-GUC-zero-rows i test cross-tenant RLS oba przechodzą.**
      `verify: ./mvnw -f backend test -Dtest=PaymentsRlsTest,MissingTenantClaimTest`
- [ ] **Test Modulith `ModularityTest` przechodzi i udowodniono, że faktycznie pada na celowym naruszeniu granicy** (Story 7.1).
      `verify: ./mvnw -f backend test -Dtest=ModularityTest`
- [ ] **Zero plików testów Playwright gdziekolwiek w repo.**
      `verify: find . -path ./node_modules -prune -o -iname "*.spec.ts" -print -o -iname "playwright.config.ts" -print | wc -l` → `0`.
- [ ] **`AGENTS.md` i wszystkie pięć plików `.claude/skills/*/SKILL.md` istnieją i są zmirrorowane do `.codex/skills/`.**
      `verify: test -f AGENTS.md && diff -r .claude/skills .codex/skills`
- [ ] **Next.js potwierdzony na `16.2.10` lub nowszym.**
      `verify: cd frontend && npm list next | grep 16.2.1`
- [ ] **Świeży klon repo, tylko przez `docker compose up` + dwie komendy `mvnw`/`npm run`, odtwarza pełny ręczny przebieg (Story 8.2) bez żadnego nieudokumentowanego kroku ręcznego.**
      `verify: ręczne — wykonaj na świeżym klonie, brak niespodzianek.`

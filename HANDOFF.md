# HANDOFF

## Zadanie

SEPA Nexus. Ta sesja (CACHE-STABLE EXECUTION CONTRACT v5): preflight, potwierdzenie baseline'u (bez pełnego re-audytu — `pom.xml`/`package.json`/lockfile niezmienione od poprzedniej sesji), weryfikacja **EPIC-23** (Frontend Foundation) jako pierwszego kandydata, następnie dwa kolejne epiki wybrane wyłącznie na podstawie realnej, potwierdzonej w kodzie zdolności (nie samego formalnego `depends_on`): **EPIC-24** (Frontend Screens, częściowo — Story 24.2) i **EPIC-25** (Konsolidacja obserwowalności, Story 25.3 częściowo). Zatrzymano się przed czwartym epikiem.

## Zrobione

### Potwierdzenie baseline'u

`./mvnw -f backend test` → `47/47` PASS (stan wejściowy). `pnpm exec tsc --version` → `6.0.3` (niezmieniony). `lint`/`typecheck`/`build`/`pnpm audit` → czyste. `git diff HEAD -- backend/pom.xml frontend/package.json frontend/pnpm-lock.yaml` → puste, więc zgodnie z instrukcją nie powtórzono pełnego audytu zależności. `podman compose ps` → Postgres/Keycloak/Kafka wszystkie `Up`.

### Analiza kandydatów (przed implementacją)

Zweryfikowano rzeczywisty stan (nie tylko `depends_on`) dla EPIC-10, 11, 19-21, 23-28, 31: `EPIC-27` (silnik korelacji pacs.002) i `EPIC-28` (granice walidacji ISO) potwierdzone jako w pełni blocked — brak realnego kanału inbound/XML; **nie wybrano `EPIC-27` zgodnie z jawną instrukcją**. `EPIC-10`/`EPIC-11` (ownership ISO-lineage/payments) potwierdzone blocked — `iso-adapter` wciąż nie jest osobnym modułem/rolą (migracja `V11` sama to przyznaje w komentarzu), `payment_status_history`/`payment_events` nie istnieją nigdzie w repo.

### EPIC-23 — Frontend Foundation → **`in-progress`** (3/4 story done)

`[AUDYT]`: przed budową sprawdzono realny stan frontendu (`find frontend/src`, `grep -rn data-testid`) — EPIC-05/06 dostarczyły pełny BFF (sesja server-side, CSRF, PKCE) + ekran `/payments` z częściową konwencją `data-testid`. Budowano na tym, nie od zera.

- **Story 23.2** (`data-testid`+deep-linki): nowy `frontend/CONVENTIONS.md`.
- **Story 23.3** (mapa rola→ekran + stany): nowy `frontend/src/components/shared/screen-state.tsx` (`ScreenStateContent`/`ScreenState`, kinds `loading|error|empty|unauthorized|unauthenticated`), `PaymentsTable` przepisany by go używać (identyczne `data-testid` zachowane). Nowy `frontend/src/lib/role-workspace-map.ts` (§9 matrix skopiowana, 7 workspace'ów, `path` tylko dla `payments`). `AppShell` przepięty na `session.claims.roles`. **Realny smoke-test przeciw żywemu Keycloak**: `submitter`→`payment_submitter` widzi `payments`; `refdata`→`reference_data_admin` widzi `[]` (workspace bez `path`) — dwa różne prawdziwi użytkownicy, dwa różne poprawne wyniki.
- **Story 23.4** (a11y checklist): nowy `frontend/A11Y-CHECKLIST.md`, każda pozycja z istniejącego `[ACCESSIBILITY]` bloku w UI-spec lub z findingu component-foundation §6.
- **Story 23.1** (codegen OpenAPI+GraphQL): **`blocked`** — brak `springdoc-openapi`/`spring-graphql` w `backend/pom.xml`, więc `npm run codegen` nie ma żadnego wejścia. Nie budowano pustego skryptu.

### EPIC-24 — Frontend Screens → **`in-progress`** (Story 24.2 częściowo, 8/9 story blocked, Playwright NIE odpalony)

Bramka Playwright (component-foundation §9 krok 8→9, `[NO-PLAYWRIGHT]`) sprawdzona na nowo, nie założona — pozostaje zamknięta, bo wymaga wszystkich pierwszych 3 ekranów, a Control Room (Story 24.1) pozostaje blocked (brak SSE/`reporting`).

- **Story 24.2** (Payments & Files): zbudowano listę+szczegół+panel ISO identifiers (**bez timeline** — brakująca tabela historii statusu, patrz otwarte pytanie #12 niżej). Nowy backend `GET /api/v1/payments/{id}` (`PaymentController`/`PaymentService.paymentDetail`/`IsoIdentifierLookup` — czyta wyłącznie przez `iso.payment_iso_identifiers`, G4), `PaymentNotFoundException`→404. Nowe testy `PaymentControllerTest` (+2). Nowy BFF `GET /api/payments/[id]`, nowy `frontend/src/app/payments/[id]/page.tsx`, link z listy.
  **Dwa realne bugi znalezione i naprawione podczas weryfikacji** (blokowały cały przepływ UI, nie tylko moją nową funkcję): (1) BFF `POST /api/payments` nigdy nie przekazywał `Idempotency-Key` do backendu — "Submit payment" był **faktycznie zepsuty** w całej ścieżce przeglądarki od czasu gdy backend zaczął wymagać tego nagłówka. Naprawiono (`payments/page.tsx` generuje `crypto.randomUUID()`, BFF wymaga+przekazuje). (2) `api/auth/callback/route.ts` czytał `realm_access.roles` z **ID tokenu**, który w tym realmie nigdy nie niesie tego claimu (potwierdzone dekodowaniem) — `SessionClaims.roles` było **zawsze puste** dla każdego realnego logowania, co czyniłoby Story 23.3 martwą w praktyce. Naprawiono: role z access tokenu (zweryfikowanego `jwtVerify` bez `audience` — access token nie ma `aud`, tylko `azp`, tak jak weryfikuje go backend). Oba potwierdzone pełnym realnym przebiegiem authorization-code+PKCE przez `curl` (prawdziwy Keycloak, prawdziwy `next start`, prawdziwy backend): login→submit(`201`)→lista→szczegół z realnym `isoIdentifiers`.
- **Stories 24.1, 24.3-24.9**: wszystkie `blocked`, każdy z konkretnym audytem w pliku epika (SSE/`reporting` brak, `ledger`/`settlement`/`egress`/`reconciliation`/`case`/`simulation`/`evidence-audit` — wszystkie zero kodu). Story 24.7 (Reference Data admin) odnotowana jako **najbliższa do odblokowania** (EPIC-12 Story 12.1 done — tabele istnieją), ale nie podjęta (CRUD backend to osobna, nietrywialna praca poza budżetem tej sesji).

### EPIC-25 — Konsolidacja obserwowalności → **`in-progress`** (Story 25.3 częściowo)

- **Story 25.3** (lag Kafka): zbudowano **lag-per-consumer-group** jako pierwszy krok — nowy `KafkaConsumerGroupLagGauge` (Micrometer gauge `kafka.consumer.lag{group,topic}`, liczony na żądanie przez `AdminClient`). `InboxConsumer`'s `@KafkaListener` dostał jawne `id`. Nowy `KafkaConsumerGroupLagGaugeTest` — **nie-próżny dowód**: zatrzymuje konsumenta, produkuje realną wiadomość, potwierdza `lag>0`, wznawia, potwierdza `lag` spada do `0`. `management.endpoints.web.exposure.include` rozszerzone o `metrics`. **Realny smoke-test**: `GET /actuator/metrics/kafka.consumer.lag` (z prawdziwym tokenem) przeciw żywemu stackowi → realna wartość `0.0`. Retry-count/DLQ-depth/alert-na-DLQ **pozostają blocked** — brak mechanizmu DLQ w `InboxConsumer` i brak topiców `csm.response`/rekoncyliacji do alarmowania; budowanie tego bez realnego celu byłoby wynajdywaniem architektury na wyrost.

### Reewaluacja EPIC-19/20/21/26 (żadna story nieodblokowana przez prace tej sesji)

Frontend/observability nie dotyka backendowych blokerów tych epików — potwierdzone, że nic się nie zmieniło: 19.2/19.4 (signature module), 20.3 (silnik korelacji/simulation), 21.2 (redesign read-modelu), 26.3/26.4 (pola ISO XML/GraphQL) — wszystkie nadal `blocked` z tych samych, wcześniej udokumentowanych powodów.

**Nowe, silniejsze uzasadnienie dodane do EPIC-19 Story 19.4**: poza rozmiarem `CanonicalMapper`, zamrożona reguła "signature verification runs before ISO XML parsing" (`CLAUDE.md`) oznacza, że żaden zgodny endpoint XML nie może istnieć, dopóki `EPIC-31` (moduł signature) ma zero kodu — Story 19.4 jest transitywnie zależne od `EPIC-31`, tak samo jak Story 19.2.

**Finalny pełny regres (2026-07-14):** `./mvnw -f backend test` → `50/50` (było `47/50`, +3 nowe testy tej sesji: 2× `PaymentControllerTest`, 1× `KafkaConsumerGroupLagGaugeTest`). `pnpm run lint/typecheck/build/audit` → czyste (TypeScript nadal `6.0.3`). `act` backend (`test`) + frontend (`build`) → oba `PASS` w realnym kontenerze CI-parity. `podman compose ps` → wszystkie trzy kontenery `Up`. `git diff --check`/`git status --short` → czyste (tylko zamierzone pliki tej sesji).

## Zmienione/nowe pliki tej sesji

- Frontend (nowe): `CONVENTIONS.md`, `A11Y-CHECKLIST.md`, `src/components/shared/screen-state.tsx`, `src/lib/role-workspace-map.ts`, `src/app/api/payments/[id]/route.ts`, `src/app/payments/[id]/page.tsx`
- Frontend (zmienione): `payments-table.tsx` (state component + row link), `app-shell.tsx` (role-filtered nav), `payments/layout.tsx` (roles wiring), `payments/page.tsx` (Idempotency-Key fix), `api/payments/route.ts` (Idempotency-Key forwarding), `api/auth/callback/route.ts` (roles z access tokenu, nie ID tokenu)
- Backend (nowe): `PaymentDetailResponse.java`, `PaymentNotFoundException.java`, `isoadapter/IsoIdentifierLookup.java`, `event/KafkaConsumerGroupLagGauge.java` + test, 2 nowe testy w `PaymentControllerTest`
- Backend (zmienione): `PaymentController`/`PaymentService`/`PaymentProblemHandler` (detail endpoint), `InboxConsumer` (jawne listener `id`), `application.yml` (+`metrics` exposure)
- Planning: `README.md` (statusy EPIC-23/24/25 + 3 nowe otwarte pytania #12-14), `EPIC-19/22/23/24/25/26-*.md`
- `docs/observability-inventory.md` — zaktualizowany status wiersza Kafka.

Nieśledzone, nie moje: `.claude/skills/impeccable/` (nie ruszane).

## Utknęliśmy na

Nigdzie technicznie. Świadomie odłożone (uzasadnione w plikach epików):
- EPIC-23: 23.1 (brak OpenAPI/GraphQL w backendzie).
- EPIC-24: 24.1 (SSE/`reporting` brak), 24.3-24.6/24.8/24.9 (moduły backendowe zero kodu), 24.7 (najbliższa, ale CRUD backend to osobna praca), timeline w 24.2 (brak tabeli historii statusu — patrz otwarte pytanie #12).
- EPIC-25: 25.2/25.4 (brak etapów pipeline'u), 25.3 retry/DLQ/alert (brak mechanizmu DLQ i topiców).
- EPIC-19/20/21/26: bez zmian, patrz poprzednie sesje + nowe uzasadnienie 19.4.

**3 nowe otwarte pytania w `planning/README.md`** (#12-14): (12) brak właściciela epika dla `payment_status_history`/`payment_events` — ani EPIC-11 ani EPIC-20 nie mają tego jako swoje zadanie budowy, tylko zakładają istnienie; (13) EPIC-19 Story 19.4 ma teraz silniejsze, architektoniczne uzasadnienie blokady (verify-before-parse); (14) `PaymentService.visiblePayments`/`paymentDetail` ograniczone do `@PreAuthorize("hasRole('payment_submitter')")`, mimo że §9 przyznaje odczyt też `payment_viewer`/`payment_approver`/`operator`/`auditor` — nie naprawione (bezpieczeństwo-wrażliwa zmiana poza wybranym zakresem).

## Plan na następny krok

1. Otwórz `planning/README.md` na nowo — nie zakładaj z pamięci.
2. Najbliższe do odblokowania: **EPIC-24 Story 24.7** (Reference Data admin — EPIC-12 Story 12.1 done, tabele istnieją, brakuje tylko CRUD backend+edytor). Alternatywnie: rozstrzygnięcie otwartego pytania #12 (kto buduje `payment_status_history`) odblokowałoby prawdziwy "timeline" w EPIC-24 Story 24.2.
3. Rozważ naprawę otwartego pytania #14 (role odczytu payments) przy najbliższej pracy nad autoryzacją — dziś `payment_viewer`/`operator`/`auditor` nie mogą realnie zobaczyć listy/szczegółu płatności mimo że §9 im na to pozwala.
4. **Nie zakładaj**, że EPIC-23/24/25 są w pełni `done` — wszystkie trzy pozostają `in-progress` ze świadomie zablokowanymi/częściowymi story.

Backend/frontend dev servery uruchomione ręcznie w tej sesji do smoke-testów (backend port 8081, frontend `next start` port 3000) — prawdopodobnie nie przetrwają do następnej sesji. Postgres/Keycloak/Kafka (Podman) powinny wciąż działać — zweryfikuj przez `podman compose -f infra/docker-compose.yml ps`.

## Pułapki, których nie wolno powtórzyć

- **Restart procesu Next.js (`next start`) czyści w-pamięci `session-store.ts`** — po `pkill`+restart trzeba przejść cały login flow (authorization code + PKCE) od nowa, stare ciasteczka sesji stają się bezużyteczne. Odkryte przy próbie ponownego smoke-testu ze starym cookie jarem (400/401 mylące, wyglądające jak inny błąd).
- **Backend wymaga `Idempotency-Key` na `POST /api/v1/payments`, ale BFF (`api/payments/route.ts`) tego nie przekazywał od momentu gdy backend zaczął tego wymagać** — realny golden path (submit przez przeglądarkę) był zepsuty niezauważenie przez conajmniej jedną poprzednią sesję. Zawsze smoke-testuj CAŁY łańcuch (przeglądarka→BFF→backend), nie tylko backend bezpośrednio — bezpośrednie testy backendu (curl z tokenem) nie wyłapały tego, bo BFF ma własną, osobną warstwę forwardowania nagłówków.
- **Role w sesji BFF muszą być czytane z access tokenu, nie z ID tokenu** — w tym realmie Keycloak `realm_access.roles` jest mapowane tylko na access token (potwierdzone dekodowaniem obu), ID token niesie tylko `tenant_id`/`branch_id`/`preferred_username`. Kod czytający `realm_access` z ID tokenu zawsze dostanie pustą tablicę ról, cicho — żaden test kompilacji/typecheck tego nie złapie, tylko realny przebieg logowania przez prawdziwy Keycloak.
- **Access token w tym realmie nie ma `aud`** (tylko `azp`) — `jwtVerify(accessToken, jwks, {audience: clientId})` rzuci błąd weryfikacji. Weryfikuj access token tylko z `issuer`, zgodnie z tym jak robi to backend `SecurityConfig`.
- **`nohup ... &` + `disown` w tym środowisku Bash bywa zawodne** (obserwowano tajemnicze "Exit code 144"/"143", proces czasem umierał mimo pozornie poprawnego uruchomienia) — użycie `run_in_background: true` parametru narzędzia Bash jest niezawodne, preferuj je do długo-działających serwerów deweloperskich zamiast `nohup`/`disown`.
- **Po `pnpm run build` uruchomionym PO wcześniejszym `next start`, stary proces nadal serwuje STARY build** — zawsze restartuj serwer PO buildzie; sprawdź `.next/BUILD_ID` timestamp vs czas startu procesu, jeśli endpoint zachowuje się jak stara wersja kodu.
- Wszystkie pułapki z poprzednich sesji (Podman nie Docker, `DOCKER_HOST` dla Testcontainers, trzy dodatkowe flagi `act`+Podman+SELinux+Ryuk, RLS GUC empty-zero-rows, `pnpm`/Node 24.18.0 — domyślny `node` w PATH bywa `24.15.0`, `ClockPort` nie rozwiązuje blokad niezwiązanych z czasem, zamknięcie epika na poziomie planningu nie oznacza automatycznie odblokowania story w innym epiku które go wymienia w `depends_on`, ArchUnit reguły potrzebują `DO_NOT_INCLUDE_TESTS`) wciąż obowiązują.

# HANDOFF

## Zadanie

SEPA Nexus, Iteracja 0. Sesja ukończyła EPIC-05 (Next.js BFF) i EPIC-06 (React/shadcn Frontend Skeleton) w pełni, zweryfikowane end-to-end. EPIC-07 (CI/CD Foundation) jest w toku (`in-progress`) — Story 7.2 i 7.3 done, Story 7.1 częściowo (workflow działa i uruchamia `ModularityTest`, ale nie udało się namacalnie potwierdzić, że test faktycznie coś wywala — patrz `[OPEN-QUESTION]` niżej, to realna, wcześniej nieznana blokada, nie coś do samodzielnego rozstrzygnięcia w tej sesji).

## Zrobione

**EPIC-05 — Next.js BFF (done, w pełni zweryfikowane).** **EPIC-06 — React/shadcn Frontend Skeleton (done, w pełni zweryfikowane).** Szczegóły identyczne jak w poprzedniej wersji tego pliku — patrz historia gita tego pliku (`git log -p -- HANDOFF.md`) dla pełnego opisu obu epików, albo bezpośrednio `planning/epics/EPIC-05-nextjs-bff.md` i `planning/epics/EPIC-06-react-shadcn-frontend-skeleton.md` (każdy task ma wpisaną faktyczną komendę weryfikacji i wynik).

**EPIC-07 — CI/CD Foundation (in-progress):**
- Story 7.2 (frontend workflow) — **done**. `.github/workflows/frontend.yml`: Node 24.18.0 (nie "Node 20" z tekstu tasku — `[PLANNING-DEFECT]`), pnpm (nie npm), lint+typecheck+build, bez Playwright. **Realny bug znaleziony i naprawiony**: `pnpm run build` pod `act` (środowisko bez `.env.local`, jak prawdziwe CI) rzucał `Missing required environment variable: KEYCLOAK_ISSUER` w kroku "Collecting page data" — `src/lib/oidc-config.ts` czytało `process.env` eagerly przy imporcie modułu, a Next.js ewaluuje moduły tras podczas builda nawet dla w pełni dynamicznych route'ów. Naprawione: `issuer`/`clientId`/`clientSecret` są teraz gettery (leniwa walidacja), `createRemoteJWKSet(...)` w `api/auth/callback/route.ts` też leniwy (`getJwks()`). Ten sam bug wystąpiłby na prawdziwym GitHub Actions bez sekretów w env builda — `act` złapał to zawczasu.
- Story 7.3 (`.actrc`, `act -l`) — **done**. `act` nie był zainstalowany — zainstalowany za zgodą użytkownika (oficjalny skrypt, `~/.local/bin`, bez sudo), `act version 0.2.89`.
- Story 7.1 (backend workflow) — **częściowo**. `.github/workflows/backend.yml` istnieje i przechodzi przez `act` (16/16 testów, `BUILD SUCCESS`), ale wymaga TRZECH dodatkowych flag ponad gołe `act -j test` z powodu rootless Podman + SELinux + zagnieżdżonego Testcontainers/Ryuk (pełna komenda i wyjaśnienie w `planning/epics/EPIC-07-ci-cd-foundation.md` Story 7.1) — te flagi są środowiskowe (per-host), nie trafiły do samego workflow YAML. **Task "wywal build na teście architektury Modulith" NIE został potwierdzony**: dwie niezależne próby deliberate-violation (referencja do zagnieżdżonego pakietu `internal`, oraz prawdziwy dwukierunkowy cykl `modules↔security`) **nie zostały wykryte** przez `ModularityTest`. Diagnostyka: Modulith wykrywa tylko DWA moduły (`modules`, `security` — bezpośrednie sub-pakiety `com.sepanexus`; `paymentlifecycle` to tylko zagnieżdżony pakiet WEWNĄTRZ modułu "modules", nie osobny moduł), i bez jawnych `@ApplicationModule`/`package-info.java` moduły są domyślnie w pełni otwarte (`Type.OPEN`) — `verify()` obecnie nie egzekwuje żadnych granic. **To pre-istniejący gap z EPIC-03, odkryty (nie stworzony) w tej sesji.** Wszystkie deliberate-violation pliki zostały posprzątane (`git diff --check` czysty, `git status` pokazuje tylko legalne zmiany z Story 6.4).

**Faktyczne PASS (2026-07-14), ten segment sesji:**
- `act -l` → listuje oba workflowy bez błędów.
- `act -W .github/workflows/frontend.yml -j build` → `Job succeeded` (bez dodatkowych flag).
- `act -W .github/workflows/backend.yml -j test --container-daemon-socket "unix://${XDG_RUNTIME_DIR}/podman/podman.sock" --container-options "--security-opt label=disable" --env TESTCONTAINERS_RYUK_DISABLED=true` → `BUILD SUCCESS`, 16/16.
- `./mvnw -f backend test` (bezpośrednio, bez act) → 16/16, w tym `ModularityTest` (przechodzi, ale patrz open question — nie wiadomo czy faktycznie egzekwuje cokolwiek).

## Utknęliśmy na

**`[OPEN-QUESTION]`**: Czy i jak domknąć rzeczywistą egzekwowalność granic modułów w `ModularityTest`? Obecnie test jest "podłączony" (uruchamia się, przechodzi), ale — z tego co ustalono empirycznie w tej sesji — nie blokuje żadnego naruszenia granic modułu, bo cała aplikacja to dziś tylko dwa domyślnie-otwarte moduły Modulith (`modules`, `security`), bez jawnie zadeklarowanych `@ApplicationModule`/`package-info.java`. To prawdopodobnie należy do przyszłego epika "ownership" (np. `EPIC-09` i dalej w `planning/README.md`, gdzie `paymentlifecycle` i przyszłe moduły dostają właściwe granice schema+Modulith) albo do rewizji EPIC-03. Nie rozstrzygnięto samodzielnie w tej sesji (poza zakresem EPIC-07, wymagałoby projektowania architektury modułów).

Backend (`spring-boot:run`) i frontend dev server (`pnpm run dev`) uruchamiane ręcznie w tej sesji do weryfikacji — prawdopodobnie nie żyją na starcie następnej sesji. Postgres/Keycloak/Kafka (Podman) powinny wciąż działać, zweryfikuj przez `podman compose -f infra/docker-compose.yml ps`.

## Plan na następny krok

1. Zdecyduj, jak domknąć `[OPEN-QUESTION]` z EPIC-07 Story 7.1 (patrz wyżej) — albo jako świadomie zaakceptowany gap odnotowany i przekazany do właściwego przyszłego epika, albo jako mini-poprawka w zakresie samego EPIC-03 (dodanie `package-info.java`/`@ApplicationModule` dla `paymentlifecycle`), **za zgodą użytkownika**, bo to dotyka architektury.
2. Po domknięciu (lub świadomym zaakceptowaniu) — ustaw Story 7.1 i EPIC-07 na `done`, zaktualizuj `planning/README.md`.
3. Zgodnie z work packetem tej sesji: **zatrzymaj się przed EPIC-08**. Otwórz `planning/epics/EPIC-08-walking-skeleton-verification.md`, sprawdź `depends_on`, przygotuj (ale nie wykonuj) pierwszy nieodhaczony task.

## Pułapki, których nie wolno powtórzyć

- **Modulith `ApplicationModules.of(...).verify()` domyślnie nie egzekwuje granic modułów bez jawnych `@ApplicationModule`/`package-info.java`** — moduły są `Type.OPEN` domyślnie. Potwierdzone empirycznie (debug test drukujący wykryte moduły + próba `verify()` na deliberate violation, dwukrotnie, różnymi metodami — internal-package i cykl — żadna nie została złapana). Nie zakładaj, że sam fakt istnienia `ModularityTest` coś chroni — sprawdź, czy moduły są faktycznie zadeklarowane.
- **Next.js 16.2.10: nie czytaj env vars na poziomie modułu (top-level, poza funkcją)** — Next.js ewaluuje moduły tras podczas `next build`'s "Collecting page data" nawet dla w pełni dynamicznych routes. Każdy `requireEnv(...)`/`createRemoteJWKSet(...)` itp. musi być za getterem/leniwym wywołaniem, inaczej build wybuchnie w CI (bez `.env.local`) nawet jeśli działa lokalnie. Wykryte przez `act` (środowisko bez sekretów), nie przez lokalny `pnpm run build` (miał `.env.local`).
- **`act` + rootless Podman + SELinux (Fedora) potrzebuje trzech dodatkowych flag** dla jobów z Testcontainers: `--container-daemon-socket unix://${XDG_RUNTIME_DIR}/podman/podman.sock` (socket w ogóle), `--container-options "--security-opt label=disable"` (SELinux blokuje dostęp do zamontowanego socketu w zagnieżdżonym kontenerze — to relabeluje TYLKO kontener joba, nie wyłącza SELinux systemowo), `--env TESTCONTAINERS_RYUK_DISABLED=true` (Ryuk nie startuje w tym zagnieżdżonym scenariuszu — realny, udokumentowany problem specyficzny dla lokalnego `act`, nie dla hostowanego GitHub runnera ani bezpośredniego `mvnw test` na hoście, gdzie Ryuk działa normalnie i NIE WOLNO go tam wyłączać).
- **Next.js 16.2.10 nie dzieli modułowych singletonów (`new Map()`) między Route Handlerami a Server Components** — kotwicz na `globalThis` (`src/lib/session-store.ts`, `src/lib/pending-auth-store.ts`).
- **Next.js 16.2.10 przemianował `middleware.ts` → `proxy.ts`**. **shadcn CLI v4 (`base-nova`/Base UI) nie ma komponentu `form`** (zastąpiony `field`) i `SidebarMenuButton` używa `render={<a .../>}`, nie `asChild`. **TypeScript 6.x nie istnieje jako stabilny release** (pin pozostaje `5.9.3`).
- `pnpm install` bez TTY wymaga `CI=true pnpm install`. Node 24.18.0 doinstalowany przez `nvm install 24.18.0` na starcie tej sesji. Backend port `8081`. `psql` niedostępny lokalnie — `podman exec -i infra_postgres_1 psql -U sepa_migration -d sepa_nexus`.
- Wszystkie poprzednie pułapki z EPIC-02/03/04 (Podman nie Docker, `DOCKER_HOST` dla Testcontainers na hoście, RLS GUC, outbox at-least-once) wciąż obowiązują.

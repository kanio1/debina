---
status: in-progress
depends_on: [EPIC-03-spring-modulith-backend-skeleton, EPIC-06-react-shadcn-frontend-skeleton]
source: "sepa-nexus-iteration-0-foundation-plan.md, EPIC 7 (Story 7.1-7.3), lines 544-564"
---

# EPIC-07 — CI/CD Foundation

Te same komendy weryfikacyjne z Epików 1-6 uruchamiane bez nadzoru, odtwarzalne lokalnie przez `nektos/act` zanim zacznie się polegać na runnerach GitHub.

## Story 7.1 — Workflow CI backendu

status: in-progress
depends_on: [EPIC-03-spring-modulith-backend-skeleton/Story 3.1]

Opis: `.github/workflows/backend.yml`, `ModularityTest` jako bramka blokująca — źródło: linie 548-553.

Kryterium ukończenia: job przechodzi lokalnie przez `act`, a celowe naruszenie granic modułu faktycznie wywala build.

Taski:
- [x] **`.github/workflows/backend.yml`**: checkout → JDK 25 → `./mvnw -f backend test` (Testcontainers potrzebuje Docker-in-Docker lub bloku `services:` Postgres+Kafka; preferuj natywne wsparcie Testcontainers dla Docker socket na hostowanym runnerze GitHub).
      `verify: act -W .github/workflows/backend.yml -j test --container-daemon-socket "unix://${XDG_RUNTIME_DIR}/podman/podman.sock" --container-options "--security-opt label=disable" --env TESTCONTAINERS_RYUK_DISABLED=true` → `BUILD SUCCESS`, `Tests run: 16, Failures: 0, Errors: 0` — PASS (2026-07-14). Zobacz notatkę o act+Podman niżej dla wyjaśnienia trzech dodatkowych flag ponad gołe `act -W ... -j test` z tekstu tasku.
- [~] **Wywal build na teście architektury Modulith** — wykonano deliberate-violation próbę, ale **nie udało się jej wywołać** z obecną konfiguracją Modulith; patrz `[OPEN-QUESTION]` niżej. Task pozostaje częściowo zweryfikowany: workflow YAML faktycznie uruchamia `ModularityTest` jako część `mvn test` (bramka jest *podłączona*), ale nie potwierdzono namacalnie, że aktualna konfiguracja modułów faktycznie *coś* wywala.

`[OPEN-QUESTION 2026-07-14]`: Próba deliberate-violation (dwie, niezależne): (1) referencja z `com.sepanexus.security.SecurityConfig` do nowo utworzonej klasy w `com.sepanexus.modules.internal.InternalOnly` (test konwencji ukrywania pakietów `internal`), (2) prawdziwy cykl dwukierunkowy `modules↔security` (dodatkowo `PaymentController` → `SecurityConfig`). Diagnostyka (`ApplicationModules.of(SepaNexusApplication.class)` debug test): Modulith wykrywa dokładnie **dwa** moduły przez domyślną konwencję (bezpośrednie sub-pakiety `com.sepanexus`: `modules` i `security` — `paymentlifecycle` to tylko zagnieżdżony pakiet WEWNĄTRZ modułu "modules", nie osobny moduł). `modules.verify()` **nie zgłosił żadnej z dwóch prób naruszenia** — ani referencji do zagnieżdżonego "internal" pakietu, ani prawdziwego cyklu. Najbardziej prawdopodobna przyczyna (per dokumentacja Spring Modulith 2.x, `fundamentals.adoc`): bez jawnej deklaracji `@ApplicationModule`/`package-info.java` na którymkolwiek z dwóch pakietów, moduły domyślnie zachowują się jak w pełni otwarte (`Type.OPEN`) — nic tu jeszcze nie ustawia ich na zamknięte. **To pre-istniejący, rzeczywisty gap architektoniczny z EPIC-03 (nie utworzony przez tę sesję)**: `ModularityTest` jest podłączony i przechodzi, ale obecnie nie egzekwuje żadnych granic modułów poza wykryciem struktury. Nie naprawiono w tej sesji — to wymagałoby jawnego zaprojektowania modułów (`package-info.java` z `@ApplicationModule`, prawdopodobnie per-bounded-context zgodnie z mapą 16 modułów z `CLAUDE.md`), co jest poza zakresem EPIC-07 (CI/CD) i należy raczej do EPIC-09+ (epiki "ownership" w `planning/README.md`) lub do rewizji EPIC-03. Zapisane też w `HANDOFF.md`.

### Notatka: `act` + rootless Podman + SELinux (Fedora)

Goła komenda `act -W .github/workflows/backend.yml -j test` (bez dodatkowych flag) **nie działa** w tym środowisku z trzech nawarstwiających się przyczyn, każda potwierdzona empirycznie:
1. `act` domyślnie nie bind-mountuje hosta Docker/Podman socketu do kontenera joba → Testcontainers wewnątrz joba nie widzi żadnego środowiska Docker. Naprawa: `--container-daemon-socket "unix://${XDG_RUNTIME_DIR}/podman/podman.sock"`.
2. SELinux (Fedora, `Enforcing`) blokuje dostęp do zamontowanego socketu wewnątrz zagnieżdżonego kontenera (`Permission denied` mimo poprawnych unixowych uprawnień `666`) — potwierdzone: `getenforce` → `Enforcing`, brak dostępu do `ausearch` (brak sudo) do potwierdzenia AVC wprost, ale zachowanie 1:1 zgadza się ze znanym wzorcem SELinux + zagnieżdżone kontenery. Naprawa: `--container-options "--security-opt label=disable"` — to relabeluje TYLKO kontener joba `act`, nie wyłącza SELinux systemowo (nie złamano zasady „nie wyłączaj SELinux”).
3. Ryuk (sidecar Testcontainers) nie startuje w tym zagnieżdżonym rootless-Podman-w-Podman scenariuszu (`Container startup failed for image testcontainers/ryuk:0.14.0`) — to jest **rzeczywisty, udokumentowany problem** specyficzny dla tej lokalnej-act-pod-Podman ścieżki (nie dla hostowanego runnera GitHub, ani dla bezpośredniego `./mvnw -f backend test` na hoście, gdzie Ryuk działa normalnie — patrz `HANDOFF.md` z EPIC-04). Naprawa zastosowana **tylko do lokalnego wywołania `act`** (nie do samego pliku workflow, który na hostowanym runnerze GitHub ma naturalny dostęp do Dockera i Ryuk tam działa): `--env TESTCONTAINERS_RYUK_DISABLED=true`.

`.actrc` przypina rodzinę obrazów (`catthehacker/ubuntu:act-latest`/`act-24.04`/`act-22.04`), ale te trzy flagi dotyczące Podman/SELinux/Ryuk są środowiskowe (per-host), nie per-workflow, więc nie trafiły do `.actrc` ani do samego YAML — są udokumentowane tutaj i w `HANDOFF.md` jako dokładna komenda do powtórzenia.

## Story 7.2 — Workflow CI frontendu

status: done
depends_on: [EPIC-06-react-shadcn-frontend-skeleton/Story 6.4]

Opis: `.github/workflows/frontend.yml`, celowo bez joba Playwright — źródło: linie 555-558.

Kryterium ukończenia: job build przechodzi lokalnie przez `act`.

`[PLANNING-DEFECT 2026-07-14]`: task poniżej mówi "Node 20" i "npm ci" — repo pinuje dokładnie Node `24.18.0` (`frontend/.node-version`, `CLAUDE.md`) i używa wyłącznie pnpm (`AGENTS.md`: "nie mieszaj lockfiles"). Workflow użył `node-version-file: frontend/.node-version` + `pnpm/action-setup` zamiast literalnego `node 20`/`npm ci`.

Taski:
- [x] **`.github/workflows/frontend.yml`**: checkout → Node 24.18.0 (z `.node-version`) → pnpm (przypięty przez `packageManager` w `package.json`) → `pnpm install --frozen-lockfile` → `pnpm run lint` → `pnpm run typecheck` → `pnpm run build`. Brak joba Playwright w tym workflow — celowo nieobecny do Iteracji 1.
      `verify: act -W .github/workflows/frontend.yml -j build` → `Job succeeded` — PASS (2026-07-14).

**Realny bug znaleziony i naprawiony podczas tej weryfikacji**: pierwsze uruchomienie przez `act` (środowisko bez `.env.local`, jak prawdziwe CI) rozwaliło `pnpm run build` — `Error: Missing required environment variable: KEYCLOAK_ISSUER` podczas kroku "Collecting page data" Next.jsa. Przyczyna: `oidcConfig.issuer`/`clientId`/`clientSecret` w `src/lib/oidc-config.ts` czytały `process.env` **eagerly przy imporcie modułu** (nie leniwie), a Next.js ewaluuje moduły tras podczas builda nawet dla w pełni dynamicznych route'ów, żeby wykryć ich runtime info — więc build wymagał prawdziwych sekretów OIDC tylko po to, żeby się skompilować. Naprawiono zamieniając te trzy pola na gettery (leniwa walidacja przy pierwszym realnym dostępie z handlera), oraz analogicznie `createRemoteJWKSet(...)` w `api/auth/callback/route.ts` (był wywoływany na poziomie modułu — zamieniony na leniwy `getJwks()`). Ten bug istniałby też na hostowanym GitHub Actions runnerze bez sekretów w środowisku builda — `act` złapał to zanim trafiło na prawdziwe CI.

## Story 7.3 — Lokalna parytet CI przez `nektos/act`

status: done
depends_on: [Story 7.1, Story 7.2]

Opis: `.actrc` przypinający rodzinę obrazów runnera — źródło: linie 560-563.

Kryterium ukończenia: `act -l` listuje oba workflowy bez błędów konfiguracji.

`act` nie był zainstalowany na starcie tej sesji; użytkownik potwierdził instalację (oficjalny skrypt instalacyjny, `~/.local/bin`, bez sudo) — `act version 0.2.89`.

Taski:
- [x] **`.actrc`** przypinający tę samą rodzinę obrazów runnera co GitHub Actions (`catthehacker/ubuntu:act-latest`/`act-24.04`/`act-22.04`, `linux/amd64`), żeby wyniki `act` były wystarczająco zbieżne z hostowanym CI, by im ufać lokalnie.
      `verify: act -l` → `Stage test test backend backend.yml push,pull_request` / `Stage build build frontend frontend.yml push,pull_request`, bez błędów konfiguracji — PASS (2026-07-14).

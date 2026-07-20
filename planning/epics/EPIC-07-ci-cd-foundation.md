---
status: done
depends_on: [EPIC-03-spring-modulith-backend-skeleton, EPIC-06-react-shadcn-frontend-skeleton]
source: "sepa-nexus-iteration-0-foundation-plan.md, EPIC 7 (Story 7.1-7.3), lines 544-564"
---

# EPIC-07 — CI/CD Foundation

Te same komendy weryfikacyjne z Epików 1-6 uruchamiane bez nadzoru, odtwarzalne lokalnie przez `nektos/act` zanim zacznie się polegać na runnerach GitHub.

## Story 7.1 — Workflow CI backendu

status: done
depends_on: [EPIC-03-spring-modulith-backend-skeleton/Story 3.1]

Opis: `.github/workflows/backend.yml`, `ModularityTest` jako bramka blokująca — źródło: linie 548-553.

Kryterium ukończenia: job przechodzi lokalnie przez `act`, a celowe naruszenie granic modułu faktycznie wywala build.

Taski:
- [x] **`.github/workflows/backend.yml`**: checkout → JDK 25 → `./mvnw -f backend test` (Testcontainers potrzebuje Docker-in-Docker lub bloku `services:` Postgres+Kafka; preferuj natywne wsparcie Testcontainers dla Docker socket na hostowanym runnerze GitHub).
      `verify: act -W .github/workflows/backend.yml -j test --container-daemon-socket "unix://${XDG_RUNTIME_DIR}/podman/podman.sock" --container-options "--security-opt label=disable" --env TESTCONTAINERS_RYUK_DISABLED=true` → `BUILD SUCCESS`, `Tests run: 16, Failures: 0, Errors: 0` — PASS (2026-07-14). Zobacz notatkę o act+Podman niżej dla wyjaśnienia trzech dodatkowych flag ponad gołe `act -W ... -j test` z tekstu tasku.
- [x] **Wywal build na teście architektury Modulith** — rozstrzygnięte w tej sesji, patrz `[PLANNING-DEFECT]` niżej (nie `[OPEN-QUESTION]` — poprzednia hipoteza o `Type.OPEN` była błędna, potwierdzono to dowodem, nie założeniem).

`[PLANNING-DEFECT 2026-07-14, rozstrzygnięty]`: Poprzednia sesja zostawiła `[OPEN-QUESTION]`: "czy `ModularityTest` egzekwuje cokolwiek", z hipotezą, że oba moduły (`modules`, `security` — bezpośrednie sub-pakiety `com.sepanexus`, wykryte domyślną konwencją pakietową Modulith) są domyślnie `Type.OPEN`. Ta hipoteza była **błędna** — zweryfikowano bezpośrednio empirycznie w tej sesji: diagnostyczny test (`ApplicationModules.of(...).forEach(m -> ... m.isOpen() ...)`, usunięty po użyciu) pokazał `type/open=false` dla obu modułów; są domyślnie **zamknięte**.

Prawdziwa przyczyna, dlaczego poprzednie próby deliberate-violation nie zostały złapane: bait-klasa użyta do testu (`InternalOnly.SECRET`, `public static final String`) jest kompilacyjną stałą Javy (compile-time constant). Javac inline'uje taką wartość bezpośrednio w bajtkodzie wołającej klasy (constant folding, JLS §13.1/§15.29) — nie generuje żadnego `getstatic`/`invokestatic` odniesienia do klasy źródłowej, tylko martwy wpis w constant poolu. ArchUnit (silnik pod spodem Modulith) analizuje rzeczywiste zależności bajtkodowe, więc taki "dowód naruszenia" nigdy nie mógł zostać wykryty — **to była wada eksperymentu, nie luka architektury**.

Powtórzono eksperyment z poprawną, niestałą referencją (`InternalOnly.secret()` jako metoda instancyjna/statyczna zwracająca wartość w runtime, nie stała): `security.SecurityConfig` wołające `modules.internal.InternalOnly.secret()` (pakiet `internal` zagnieżdżony w module `modules`, więc nie jest częścią API modułu) →
```
org.springframework.modulith.core.Violations:
- Module 'security' depends on non-exposed type com.sepanexus.modules.internal.InternalOnly within module 'modules'!
Method <com.sepanexus.security.SecurityConfig.deliberateViolation()> calls method <com.sepanexus.modules.internal.InternalOnly.secret()> in (SecurityConfig.java:25)
```
→ `BUILD FAILURE`, dokładnie jak oczekiwano — **EXPECTED FAIL potwierdzony**. Po usunięciu eksperymentu (`modules/internal/`, diagnostyczny test, referencja w `SecurityConfig`) → `./mvnw -f backend test` → `Tests run: 16, Failures: 0, Errors: 0`, `BUILD SUCCESS` — PASS ponownie (2026-07-14). `git diff --check` i `git status --short` czyste (tylko pre-istniejący nieśledzony `.claude/skills/impeccable/`).

Wniosek: `ModularityTest` **rzeczywiście egzekwuje granice modułów** już teraz, dla obu wykrytych modułów (`modules`, `security`), mimo braku jawnych `@ApplicationModule`/`package-info.java` — domyślna konwencja pakietowa Spring Modulith 2.x jest wystarczająca do zamkniętych (closed) modułów z ukrytymi pakietami wewnętrznymi. Docelowy podział na ~16 modułów z mapy w `CLAUDE.md` (`ingress`, `iso-adapter`, `payment-lifecycle`, `signature`, `routing`, ...) to osobna, świadomie odłożona praca (epiki "ownership", EPIC-09+), nie blokada CI/CD.

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

## Story 7.4 — Scheduled background failures are operationally visible

status: done
depends_on: [EPIC-18-per-schema-outbox-inbox-rollout/Story 18.5]

Opis: controlled scheduled relay failure updates an operational health state and is asserted by a
dedicated Testcontainers runtime test. Source: `DEBINA-GAP-RISK-BACKLOG.md` TEST-GAP-002 and
`DEBINA-COMPREHENSIVE-PAYMENTS-ASSESSMENT.md` §32 identify the current scheduler error as an
unrepresented runtime failure.

Kryterium ukończenia story: scheduler execution is isolated from unrelated tests; permission and
broker failures leave the event unpublished, turn `outboxRelay` health DOWN, and a later success
returns it to UP without an uncontrolled background exception.

**[DONE 2026-07-20]** Base test configuration disables scheduling; only
`ScheduledRelayOperationalRuntimeTest` enables it, with a short test-only delay. In PostgreSQL
18/Kafka Testcontainers it revokes only `SELECT` from `outbox_dispatcher_role`, inserts an event,
and observes the actual scheduled payment relay leave it unpublished and expose
`DATABASE_PERMISSION` through `outboxRelay` health. Restoring exactly
`SELECT, UPDATE(published_at)` causes the same scheduled path to publish the row after a Kafka
acknowledgement and return health to UP. `OutboxRelayKafkaFailureTest` independently proves the
broker-acknowledgement rollback/redelivery path. Removing the payment `@Scheduled` annotation made
the runtime test fail (health stayed UP), then restoration reran green. Per-relay snapshots prevent
an ISO success from masking a payment relay failure.

Taski:
- [x] **Add scheduling isolation and the relay operational-health contributor.** Production scheduling remains enabled; base tests opt out; the dedicated runtime test opts in.
      `verify: ./mvnw -f backend test -Dtest=ScheduledRelayOperationalTruthTest,ScheduledRelayOperationalRuntimeTest,ScheduledRelayActivationIntegrationTest,OutboxRelayKafkaFailureTest` → PASS (2026-07-20; PostgreSQL 18/Kafka + scheduler mutation proof).

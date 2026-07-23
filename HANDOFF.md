# HANDOFF

## Zadanie

Debina jest syntetyczną platformą badawczą SEPA/ISO 20022. Sesja odtworzyła stan lokalnego Phase D wyłącznie z worktree/historii/dokumentów, naprawiła Dagger Go SDK graphs i doprowadziła publiczne gate’y `fast`, `integration`, `smoke`, `phase-d` oraz `all` do lokalnego runtime proof bez push, remote CI i produkcyjnych systemów.

## Zrobione

### Stan początkowy

- Branch: `rebase/enterprise-evolution`.
- Początkowy HEAD: `e7e90e1 continuation: refactor(ci): add phase d smoke runtime`.
- Początkowy worktree i index: czyste; zastany `HANDOFF.md` był nieaktualny względem commitowanego D3B/D6.
- Początkowy SHA-256 chronionego `build/generated-spring-modulith/javadoc.json`: `47b1b89f63804b4062cd6abe9242a7d56b2212636de95a64784d53723c03e054`.
- Toolchain: Dagger CLI/Engine `v0.21.4`, Go `1.26.5-X`, rootful Podman `5.8.4`, cgroups v2; lokalny minimalny graph przeszedł.

### Implementacja i klasyfikacja

- D3B JSON_DIRECT — `FOCUSED-RUNTIME-PROVEN` i `AGGREGATE-RUNTIME-PROVEN`: realny browser → frontend → BFF → REST command → PostgreSQL/ISO/outbox → exact Kafka payment key. Naprawiono Web Crypto UUID fallback, jawne HTTP 201/persistent payment ID, psql variable input, prawa pliku UUID i Kafka key-only evidence.
- D3B maker-checker — `FOCUSED-RUNTIME-PROVEN` i `AGGREGATE-RUNTIME-PROVEN`: osobne BrowserContext maker/checker, maker 403, checker approval, approval/audit/outbox i exact Kafka key. Nie scalono approval z business-status axis.
- D3B Payment Detail lineage — `FOCUSED-RUNTIME-PROVEN` i `AGGREGATE-RUNTIME-PROVEN`: realne identyfikatory/status/timeline/lineage oraz korelacja PostgreSQL/outbox/Kafka, bez syntetyzowania nieobecnych ISO facts.
- D6 non-browser leaves — `FOCUSED-RUNTIME-PROVEN` i `AGGREGATE-RUNTIME-PROVEN`: child non-zero, bounded timeout, PostgreSQL/Kafka/Keycloak/backend/frontend unavailable. `expectedFailure` używa `Expect: FAILURE`, sprawdza rzeczywisty non-zero i dokładny marker z stderr; przypadkowy non-zero nie jest sukcesem.
- Browser navigation failure — `FOCUSED-RUNTIME-PROVEN` i `AGGREGATE-RUNTIME-PROVEN`: Playwright `page.goto` do niedostępnego graph-local aliasu, bounded 30 s wrapper, rozpoznanie błędu nawigacji i child exit 23.
- Failure artifacts — `FOCUSED-RUNTIME-PROVEN` i `AGGREGATE-RUNTIME-PROVEN`: raw diagnostic pozostaje wewnątrz kontenera; eksportowany jest tylko 573-bajtowy JSON summary. Automatyczny scan odrzuca wymagane wzorce oraz dokładne syntetyczne wartości sekretów.
- Cache reuse — `FOCUSED-RUNTIME-PROVEN` i `AGGREGATE-RUNTIME-PROVEN`: pierwszy `cache-probe` wykonał vertex, identyczny drugi call miał `DebinaVerification.cacheProbe CACHED` i ten sam digest.
- Cache invalidation — `FOCUSED-RUNTIME-PROVEN` i `AGGREGATE-RUNTIME-PROVEN`: zmiana tylko `source-input` ponownie wykonała probe, dała inny digest, a wcześniejsze `Directory.file`/`Container.withFile` pozostały `CACHED`; osobno potwierdzono config invalidation.
- Aggregate propagation — `FOCUSED-RUNTIME-PROVEN`: `aggregate-unexpected-failure-probe` zakończył się oczekiwanym non-zero i zachował `unexpected-child: PHASE-D UNEXPECTED CHILD FAILURE`.
- `smoke-payments` — `AGGREGATE-RUNTIME-PROVEN`: trzy browser journeys sekwencyjnie w jednej sesji, 3:27.06, exit 0. PostgreSQL, Kafka, Keycloak i Keycloak PostgreSQL mają per-journey instance identity, więc Dagger nie deduplikuje stanowych usług.
- `phase-d` — `AGGREGATE-RUNTIME-PROVEN`: `fast + integration + smoke-auth + smoke-payments + assurance`, finite per-child contexts, 1:41.53, exit 0.
- `fast`, `integration`, `smoke`, `all` — osobne publiczne calls, wszystkie exit 0. `smoke` pozostaje kompatybilnym D3A; `all` bez duplikacji deleguje do socket-free `phase-d`; typed-socket Testcontainers regression pozostaje osobny.

### Naprawione root causes

- `crypto.randomUUID` nie było dostępne na niesecure graph-local HTTP origin → Web Crypto `getRandomValues` RFC 4122 v4 fallback w `frontend/src/lib/browser-random-uuid.ts`.
- `psql -c` nie interpolował `-v payment_id` → SQL przekazywany przez stdin/heredoc.
- Nie-root Kafka image nie czytał UUID file → jawne read-only permissions `0444`.
- Kafka evidence szukał serializacji payload i czekał na 20 rekordów → maksymalnie 1 rekord, `print.value=false`, pełne porównanie exact UUID key.
- Marker maker fixture był dołączany po immutable `WithExec` → inputs są wstrzykiwane przed uruchomieniem Playwright.
- Maker proof błędnie wymagał `payment.status=RECEIVED` razem z approval axis → zachowano osobne approval/audit/outbox evidence zgodnie z pięcioma osiami statusu.
- `expectedFailure` akceptował dowolny SDK `exit code:` → Dagger `Expect: FAILURE` udostępnia exit/stderr parentowi; wymagany jest dokładny marker.
- `smoke-payments` współdzielił identycznie zdefiniowaną Kafka/Keycloak usługę między journeys → per-instance labels zapobiegają Dagger service deduplication.

### Dokładne proof commands i wyniki

- `dagger call smoke-json-direct-submission --progress=plain` → `PHASE-D JSON_DIRECT EVIDENCE VERIFIED`, 1 Playwright passed, 1:20.04, exit 0.
- `dagger call smoke-maker-checker-approval --progress=plain` → `PHASE-D MAKER-CHECKER EVIDENCE VERIFIED`, 1 Playwright passed; final key-only focused proof 1:16.43, exit 0.
- `dagger call smoke-payment-detail-lineage --progress=plain` → `PHASE-D DETAIL-LINEAGE EVIDENCE VERIFIED`, 1 Playwright passed, 1:06.26, exit 0.
- `dagger call smoke-payments --progress=plain` → wszystkie trzy markery, 3:27.06, exit 0.
- `dagger call resilience-{child-non-zero,bounded-timeout,postgres-unavailable,kafka-unavailable,keycloak-unavailable,backend-unavailable,frontend-unavailable} --progress=plain` → wszystkie dokładne `PHASE-D EXPECTED ...`, exit 0.
- `dagger call resilience-browser-navigation-failure --progress=plain` → `PHASE-D EXPECTED BROWSER_NAVIGATION_FAILED`, 5.75 s, exit 0.
- `dagger call failure-artifacts export --path=/tmp/debina-phase-d-artifacts --progress=plain` oraz `dagger call failure-artifact-redaction --progress=plain` → export i `PHASE-D FAILURE-ARTIFACT REDACTION VERIFIED`.
- Dwa identyczne `dagger call cache-probe --source-input=phase-d-runtime-proof-v1 --configuration=browser-chromium --progress=plain` → cold exec, potem function `CACHED`, digest `1fd83a37...6df5a`.
- `dagger call cache-probe --source-input=phase-d-runtime-proof-v2 --configuration=browser-chromium --progress=plain` → exec ponowiony, digest `80ce5e5e...8c826`; niezależne wcześniejsze layers `CACHED`.
- `dagger call cache-invalidation --progress=plain` → `PHASE-D CACHE-SOURCE-INVALIDATED` i `PHASE-D CACHE-CONFIG-INVALIDATED`.
- `dagger call aggregate-unexpected-failure-probe --progress=plain` → oczekiwany exit 1, exact unexpected child zachowany.
- `dagger call phase-d --progress=plain` → exit 0, 1:41.53.
- `dagger call fast --progress=plain` → exit 0, 2.21 s.
- `dagger call integration --progress=plain` → exit 0, 2.12 s.
- `dagger call smoke --progress=plain` → exit 0, 4.60 s.
- `dagger call all --progress=plain` → exit 0, 6.50 s.
- Po ostatniej zmianie: `gofmt -l .` (brak outputu), `go test ./...`, `go vet ./...`, `dagger functions --progress=plain`, `git diff --check`, frontend `pnpm run typecheck`, `pnpm run lint` → wszystko exit 0; lint ma jedno istniejące ostrzeżenie TanStack i 0 errors.

### Artifacts, commity i protected files

- Lokalne, niecommitowane runtime logs: `/tmp/debina-phase-d-*.log`.
- Lokalny, niecommitowany redacted artifact: `/tmp/debina-phase-d-artifacts/failure-summary.json`; forbidden-pattern scan `PASS`.
- Commity:
  - `647aa5d fix(ci): runtime-prove phase d payment journeys` — trzy focused D3B proofy i ich root-cause fixes.
  - `6033f3a test(ci): harden phase d assurance and graph isolation` — D6/navigation/artifact/cache oraz stateful service isolation.
  - `6f059b1 feat(ci): compose phase d public gates` — finite sequential compositor, `SmokeAuth`, `SmokePayments`, `PhaseD`, `All`, unexpected propagation.
  - `dcf2f7a docs(ci): record phase d runtime proof` — manifest i architektura zgodne z wykonanym proofem.
- `build/generated-spring-modulith/javadoc.json`: początkowy i końcowy SHA-256 `47b1b89f63804b4062cd6abe9242a7d56b2212636de95a64784d53723c03e054`.
- `infra/keycloak/realm-export.json`, secrets, certyfikaty i dependency lockfiles nie mają przypadkowego diffu. Nie wykonano `git push`.

## Utknęliśmy na

Nic nie blokuje Phase D; wszystkie wymagane gates i assurance mają runtime proof. W logu maker-checker backend dwukrotnie odnotował nieblokujący `NullPointerException` przy mapowaniu ogólnej listy płatności, gdy `PaymentEntity.getStatus()` jest `null` dla paymentu oczekującego na approval. Approval Queue, maker denial, checker command, audit/outbox i Kafka evidence działają; ten symptom nie był maskowany zmianą domeny ani testu i powinien być osobnym defektem source/API.

## Plan na następny krok

W następnej sesji najpierw odtwórz w wąskim teście backend API przypadek odczytu paymentu `PENDING_APPROVAL` z `null` business status i napraw mapper tak, aby zachować oddzielne status axes bez wymuszania fałszywego `RECEIVED`.

## Pułapki, których nie wolno powtórzyć

- Nie usuwaj per-journey labels Kafka/Keycloak/PostgreSQL: identyczne stateful service definitions są deduplikowane przez Dagger w jednym aggregate.
- Nie wracaj do payload grep ani `max-messages 20`; evidence koreluje exact Kafka key i nie eksportuje payloadu.
- Nie uznawaj dowolnego child non-zero za expected failure; wymagaj `Expect: FAILURE`, non-zero i dokładnego markeru.
- Nie łącz approval status z business/ISO/finality/transport/receipt axes; pending approval może legalnie mieć `null` business status.
- Nie dołączaj marker files po `WithExec`; Dagger containers są immutable.
- Nie loguj raw diagnostics, env, headers, cookies ani sekretów; eksportuj wyłącznie redacted artifact i zawsze uruchamiaj automatyczny scan.
- Nie dodawaj typed Podman socketu do no-argument gates; Testcontainers regression pozostaje osobnym explicit-socket proofem.
- Nie zmieniaj canonical realm, migracji ani chronionego generated javadoc jako skutku ubocznego testów.

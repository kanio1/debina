# HANDOFF

## Zadanie

Debina jest syntetyczną platformą badawczą SEPA/ISO 20022. Na gałęzi `rebase/enterprise-evolution` trwa implementacja lokalnych, socket-free pipeline’ów Dagger Phase D według zatwierdzonego blueprintu: bez Phase E, Wave 12, remote CI, push, uruchamiania Chromium i wykonywania payment journeys.

## Zrobione

- Preflight jest potwierdzony: branch `rebase/enterprise-evolution`, HEAD `1ba27bffa59e7751ee9dabdef6b0fa01bd35989b` zawiera `7475475`; lokalny Dagger v0.21.4 działa z rootful Podman 5.8.4 przez `unix:///run/podman/podman.sock` (`LOCAL-DAGGER-RUNTIME-READY`).
- Chroniony `build/generated-spring-modulith/javadoc.json` nadal ma SHA-256 `47b1b89f63804b4062cd6abe9242a7d56b2212636de95a64784d53723c03e054`; canonical Keycloak realm i Wave 12 pozostają nietknięte.
- D0 jest lokalnie zatwierdzone jako `f82cd4f refactor(ci): centralize phase d credential authority`: graph-local `phaseDCredentials` przekazuje role/passwords jako Dagger secrets, bez plaintext PostgreSQL/Flyway haseł w zmienionej konfiguracji.
- D1 jest lokalnie zatwierdzone jako `1ba27bf refactor(ci): add phase d smoke runtime`: jeden świeży, socket-free graf PostgreSQL/Kafka/Keycloak/backend/frontend na D3B leaf, z migracją i credential proof przed backendem.
- Niezatwierdzone D2–D4 dodają trzy source-backed public functions: `smoke-json-direct-submission`, `smoke-maker-checker-approval`, `smoke-payment-detail-lineage`; każdy tworzy własny graph, przekazuje z Playwright tylko UUID paymentu, potem weryfikuje PostgreSQL i Kafka bez tokenów, cookies ani body sesji.
- D2 używa realnej ścieżki UI → BFF → `POST /api/v1/payments` dla `JSON_DIRECT`, ISO/raw lineage i `payment.received.v1`. D3 seeduje istniejącą `reference_data.approval_matrix_rules` przez `reference_data_role` z tenant GUC i weryfikuje maker/checker/audit. D4 pokrywa istniejący Payment Detail: identyfikatory ISO, stan, timeline oraz korelację DB/Kafka — bez nieistniejącego UI audit drawer.
- Dodano niezależny, niestage’owany podzbiór D6: `resilience-{child-non-zero,bounded-timeout,postgres-unavailable,kafka-unavailable,keycloak-unavailable,backend-unavailable,frontend-unavailable}`. Wszystkie używają świeżych finite clients, zwracają wyłącznie `PHASE-D EXPECTED <KLASYFIKACJA>` po obserwacji Daggerowego non-zero i nie otwierają host portu/socketu. Każdy command został uruchomiony jednokrotnie i przeszedł w 3–7 s; nie uruchomiono browsera.
- Po D4/D6 przeszły proofy statyczne: `gofmt`, `go test ./...`, `go vet ./...`, `dagger functions`, `dagger check fast --progress=plain`, `git diff --check`. Fast ma tylko istniejące ostrzeżenie ESLint TanStack; zakończył się sukcesem.

## Utknęliśmy na

Nie ma blokady technicznej ani produktowej, ale trwa rozbieżność w poleceniu: volatile resume każe komitować kolejne proven waves, zaś stabilna sekcja wyraźnie zakazuje Chromium i payment journeys. D2–D4 są zatem wyłącznie static-proven; ich Dagger function graphs nie były uruchamiane, nie zostały staged ani committed i nie wolno ich przedstawiać jako runtime-proven. D6 nie jest kompletny: browser-navigation, redacted failure-artifacts, cache/invalidation i final aggregate pozostają po D3B proof.

## Plan na następny krok

Jeżeli zakaz Chromium nadal obowiązuje, pozostaw D2–D4/D6 jako niestage’owane i nie przechodź do agregacji/D4 assurance, bo ich precondition wymaga runtime proof. Po jawnym zniesieniu zakazu uruchom kolejno i pojedynczo `dagger call smoke-json-direct-submission --progress=plain`, `dagger call smoke-maker-checker-approval --progress=plain`, `dagger call smoke-payment-detail-lineage --progress=plain`; po każdym source-contract failure napraw tylko Dagger/test design, a po proofie wykonaj oddzielny lokalny commit zgodnie z waves. Nie uruchamiaj ponownie unchanged failed graph ani pełnego Testcontainers regression.

## Pułapki, których nie wolno powtórzyć

- Zachowaj `dagger check smoke` jako kompatybilny D3A login/session/health; przyszłe D3B ma osobne `SmokePayments`, nie zmianę starego smoke.
- Nie przekazuj Podman socketu do `fast`, `integration`, D3B, D4 ani aggregate; Testcontainers pozostaje wyłącznie explicit typed `*dagger.Socket`.
- Nie drukuj haseł, tokenów, cookies ani body sesji; utrzymaj jeden `phaseDCredentials` bundle per graph i `WithSecretVariable`.
- Nie zmieniaj `infra/keycloak/realm-export.json`, migracji, `build/generated-spring-modulith/javadoc.json` ani Wave 12; nie stage’uj `HANDOFF.md` w commitach Phase D.

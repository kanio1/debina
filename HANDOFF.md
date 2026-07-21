# HANDOFF

## Zadanie

Wave 6 dostarczył źródłowo potwierdzone odblokowania Keycloak oraz skonsolidował nierozstrzygalne kontrakty settlement/routing. Platforma pozostaje syntetycznym środowiskiem nauki, bez twierdzeń regulacyjnych.

## Zrobione

- Historyczny Wave 5 pozostaje dowodem: deferred cycle FSM, G6 locking, netting, `ON_CYCLE_SETTLED`, V51/V52 i `CutoffStateReader`; nie odtwarzano go ani nie liczono ponownie.
- **EPIC-74 Story 74.1 — done**, commit `2355304`: `infra/keycloak/realm-export.json` ma jedną Organization `demo-bank-org`, Organization-owned `tenant_id`, user-owned `branch_id`, `sepa-guc`, 12 ról, trzy klientów i rozdzielony seed submitter/approver. `SecurityConfig` normalizuje dokładny nested claim Keycloak tylko dla jednego Organization i nie wybiera tenant przy wieloznaczności. `FullRoleModelRealmTest` (2), `MakerCheckerSeedHygieneTest` (1) i `SecurityConfigTest` (3) PASS; pierwsze dwa uruchamiają prawdziwy Keycloak 26.6.4 i wystawiają token.
- **EPIC-74 Story 74.5 — done**, commit `33e1193`: ADR-N14, osobny PostgreSQL 18 `keycloak-postgres`, `backup-keycloak.sh` i checksummed dump/export. Runtime proof: backup zatrzymuje/wznawia wyłącznie Keycloak, dump został odtworzony do izolowanego PostgreSQL 18 (`realm=2`), a export realmu przechodzi `jq` (`sepa-nexus`, Organizations enabled).
- Pełne backend regresje po ostatniej zmianie: dwa niezależne uruchomienia `./mvnw -f backend test` — **489/489 PASS** każde. Logi: `/tmp/DEBINA-DECISION-AND-CAPABILITY-UNLOCK-WAVE-6/backend-regression-{1,2}.log`.

## Nadal zablokowane

- D6-01 cutoff/cycle primary outcome i consequence — EPIC-55/55.2, 55.4.
- D6-02 precheck request/result/outcome matrix — EPIC-55/55.3, EPIC-40/40.2.
- D6-03 internal-book eligibility/account mapping/failure projection — EPIC-38/38.1.
- D6-04 file-batch assignment contract — EPIC-38/38.2.
- D6-05 queue eligibility/lifecycle/named target cycle — EPIC-40/40.2--40.3.

Wszystkie pięć to Class C i są dokładnie opisane w `planning/decisions/DEBINA-WAVE-6-CONSOLIDATED-DECISION-PACKET.md`. EPIC-50/50.1 nie ma retry/DLQ/delivery facts. EPIC-74/74.2--74.4 są P1/iteration-blocked (brak implementowalnego inventory admin commands, step-up policy i pełnego FAPI/passkey contract), nie należy ich rozszerzać z samej nazwy technologii.

## Następny krok

Uzyskać odpowiedź na D6-01, następnie ponownie ocenić EPIC-55 Story 55.2 i zbudować pierwszy RED proof zatwierdzonego primary routing outcome. Nie wybierać samodzielnie konsekwencji biznesowej.

## Bezpieczeństwo sesji

- Nie było push.
- Testy Maven zmieniają `build/generated-spring-modulith/javadoc.json`; przywróć go, jeżeli zmiana nie jest intencjonalna.
- Local Compose Keycloak i `keycloak-postgres` zostały zatrzymane po proofie; żadne wolumeny nie zostały usunięte.

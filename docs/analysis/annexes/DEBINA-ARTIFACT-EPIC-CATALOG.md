# Aneks A — katalog artefaktów i klasyfikacja 76 EPIC-ów

**Stan dowodów:** 2026-07-17. **Repozytorium:** `Playwright-Learning-Development` / SEPA Nexus. Nazwa „Debina” w raporcie oznacza audytowany produkt w tym repozytorium. Ten aneks jest częścią [raportu głównego](../DEBINA-COMPREHENSIVE-PAYMENTS-ASSESSMENT.md).

## A.1. Reguła klasyfikacji

`done` i `in-progress` są stanami frontmatter, a nie automatycznym dowodem realizacji procesu. Werdykt dowodowy jest nadawany osobno: **POTWIERDZONE** — kod, migracja i proporcjonalny test; **CZĘŚCIOWO POTWIERDZONE** — działający fragment bez pełnego E2E; **TYLKO ZAPROJEKTOWANE** — backlog/blueprint; **SPRZECZNE** — artefakty nie zgadzają się; **BRAK DANYCH** — brak wymaganego dowodu. Wszystkie 76 plików epików zostały objęte klasyfikacją grupową w tabeli A.3; wszystkie 279 stories zostały policzone i powiązane z ich plikiem źródłowym przez `planning/story-inventory.json`. Audyt nie twierdzi, że każde story zostało ręcznie ocenione słowo po słowie: graf oznacza 261/279 jako `NOT_DEEP_DIVED`.

## A.2. Katalog głównych artefaktów

| Artefakt | Lokalizacja | Typ / wersja / data | Właściciel / obszar | Wiarygodność i relacje |
|---|---|---|---|---|
| ADR index i frozen decisions | `README.md`, `ADR-N1`–`ADR-N8` | normatywny, bieżący | architektura | **Wysoka**; nadrzędny wobec blueprintów i kodu |
| Instrukcje repo | `AGENTS.md` + lokalne `*/AGENTS.md` | normatywny, bieżący | governance | **Wysoka**; definiuje produkt jako synthetic QA/SDET lab, nie bank/CSM |
| Status backlogu | `planning/README.md` | indeks operacyjny, 2026-07-17 | product/planning | **Średnia–wysoka**; trzy rozbieżności ze stanem frontmatter |
| Katalog stories | `planning/story-inventory.json` | generowany, 76/279 | planning | **Wysoka dla liczb**; walidatory PASS |
| Capability graph | `planning/capabilities.yaml` | 144 nodes, 256 edges | planning | **Średnia**; DAG bez cykli, lecz tylko 68 story nodes i status drift |
| Redesign backlogu | `planning/BACKLOG-REDESIGN.md` | model capability-first | planning | **Wysoka** dla metody, nie dla implementacji |
| Message/data blueprint | `sepa-nexus-message-flow-and-data-blueprint.md` | blueprint §3.7 v2 | domain/data/integration | **Wysoka projektowo**; źródło zdarzeń, DDL sketches i topiców |
| Ownership blueprint | `sepa-nexus-blueprint-ownership-integration.md` §3.6 | blueprint | module/data ownership | **Wysoka**; rozwija ADR one-writer |
| Full blueprint review | `sepa-nexus-full-blueprint-review-and-task-plan.md` | historyczny plan | product/domain | **Średnia**; zawiera 16-flow matrix i jawnie deferuje SDD |
| Domain analysis | `sepa-nexus-domain-analysis-review.md` | analiza | domain | **Średnia–wysoka**; projekt, nie dowód kodu |
| Security blueprint | `sepa-nexus-keycloak-26-security-architecture-blueprint.md` | target model | security | **Wysoka projektowo**; target 12 ról, implementacja ma 4 |
| Signature blueprint | `sepa-nexus-signature-module-blueprint.md` | frozen module design | signature/security | **Wysoka**, zgodna z EPIC-31 |
| Frontend blueprints/specs | `sepa-nexus-react-nextjs-frontend-blueprint.md`, trzy screen specs, component foundation | projekt UI | operations/UI | **Średnia**; 9 ekranów planowanych, thin slice zrealizowany |
| Test-learning blueprints | `sepa-nexus-playwright-test-learning-business-development.md`, `sepa-nexus-playwright-learning-vision-and-success-criteria.md` | learning scope | QA | **Wysoka dla celu**, brak Playwright w kodzie |
| API implementation | `backend/.../PaymentController.java` | kod Spring | payment API | **Wysoka**; pięć grup operacji, jedyny controller biznesowy |
| Backend build | `backend/pom.xml`, `.mvn/wrapper` | Boot 4.1.0, Java 25, Modulith 2.1.0, Maven 3.9.11 | platform | **Wysoka**; rzeczywiste wersje build |
| DB migrations | `backend/src/main/resources/db/migration/V1__...V28__...sql` | Flyway 28 migracji | data | **Wysoka**; PostgreSQL 18.4 potwierdzony testem |
| AsyncAPI | `infra/asyncapi/asyncapi.yaml` | katalog 28 topiców | integration | **Średnia**; większość future contract, payloady `{}` |
| Keycloak realm | `infra/keycloak/realm-export.json`, compose | Keycloak 26.6.4 | IAM | **Wysoka** dla local-dev; 4 role, 4 seeded users |
| Infrastructure | `infra/docker-compose.yml` i profile | PostgreSQL 18, Kafka image `latest` | platform | **Średnia**; Kafka runtime nieprzypięty |
| Frontend | `frontend/package.json`, `frontend/src/**` | Next 16.2.10, React 19.2.7, TS 6.0.3 | UI/BFF | **Wysoka**; 47 source files, brak test/spec files |
| Testy backendu | `backend/src/test/**` | 67 test classes | QA | **Wysoka lokalnie**, lecz full suite niehermetyczny i przepuszcza błędy schedulerów |
| OpenAPI/GraphQL/protobuf/XSD | repo-wide search | brak wykonawczych kontraktów | integration | **BRAK DANYCH / niezaimplementowane**; GraphQL planowany, EPC TVS/XSD nieobecne |
| Schematy/backup/DR/retencja | repo-wide search | brak operacyjnych procedur | operations/data | **BRAK DANYCH** |

Duplikaty są w większości świadomymi współdzielonymi capabilities (m.in. 33.1=36.1, EPIC-34=37, 49=69). Realny konflikt zakresu zapisano dla 43.2 vs 45.2 (render–sign–deliver). Artefakty porzucone nie zostały jednoznacznie oznaczone; dokument historyczny nie może być użyty jako dowód wdrożenia.

## A.3. EPIC assessment — wszystkie 76 EPIC-ów

| EPIC | Plik | Frontmatter | Klasyfikacja biznesowo-techniczna |
|---|---|---|---|
| EPIC-00 | `planning/epics/EPIC-00-repository-agent-foundation.md` | done | POTWIERDZONE — governance/foundation, nie flow płatniczy |
| EPIC-01 | `planning/epics/EPIC-01-postgresql-foundation.md` | done | POTWIERDZONE — PostgreSQL/Flyway foundation |
| EPIC-02 | `planning/epics/EPIC-02-keycloak-realm-iteration-0.md` | done | CZĘŚCIOWO POTWIERDZONE — local realm, nie target authorization |
| EPIC-03 | `planning/epics/EPIC-03-spring-modulith-backend-skeleton.md` | done | POTWIERDZONE — skeleton/module verification |
| EPIC-04 | `planning/epics/EPIC-04-outbox-inbox-kafka-thin.md` | done | SPRZECZNE — pattern istnieje, runtime grants/topic semantics błędne |
| EPIC-05 | `planning/epics/EPIC-05-nextjs-bff.md` | done | POTWIERDZONE dla thin BFF |
| EPIC-06 | `planning/epics/EPIC-06-react-shadcn-frontend-skeleton.md` | done | POTWIERDZONE dla skeletonu, bez E2E |
| EPIC-07 | `planning/epics/EPIC-07-ci-cd-foundation.md` | done | CZĘŚCIOWO POTWIERDZONE — brak Keycloak/Playwright i hermetyczności |
| EPIC-08 | `planning/epics/EPIC-08-walking-skeleton-verification.md` | done | CZĘŚCIOWO POTWIERDZONE — test zależy od Keycloak localhost |
| EPIC-09 | `planning/epics/EPIC-09-ownership-schema-grants.md` | done | CZĘŚCIOWO POTWIERDZONE — dobre testy, wykryte runtime wiring gaps |
| EPIC-10 | `planning/epics/EPIC-10-iso-lineage-ownership.md` | in-progress | CZĘŚCIOWO POTWIERDZONE / decision gate |
| EPIC-11 | `planning/epics/EPIC-11-payment-slim-ownership.md` | in-progress | CZĘŚCIOWO POTWIERDZONE |
| EPIC-12 | `planning/epics/EPIC-12-reference-data-ownership.md` | blocked | CZĘŚCIOWO POTWIERDZONE DDL; CRUD/ownership decision blocked |
| EPIC-13 | `planning/epics/EPIC-13-ledger-ownership.md` | in-progress | CZĘŚCIOWO POTWIERDZONE DDL/grants |
| EPIC-14 | `planning/epics/EPIC-14-egress-boundary-ownership.md` | not-started | SPRZECZNE z `planning/README.md`; część child stories done |
| EPIC-15 | `planning/epics/EPIC-15-kafka-topic-ownership.md` | blocked | SPRZECZNE contract/runtime; decision blocked |
| EPIC-16 | `planning/epics/EPIC-16-read-model-graphql-ownership.md` | blocked | TYLKO ZAPROJEKTOWANE / GraphQL absent |
| EPIC-17 | `planning/epics/EPIC-17-simulation-path-enforcement.md` | blocked | TYLKO ZAPROJEKTOWANE; brak engine owner |
| EPIC-18 | `planning/epics/EPIC-18-per-schema-outbox-inbox-rollout.md` | in-progress | CZĘŚCIOWO POTWIERDZONE; payment/iso/egress, runtime niespójny |
| EPIC-19 | `planning/epics/EPIC-19-ingress-staging-pipeline.md` | done | CZĘŚCIOWO POTWIERDZONE — intake działa, downstream nie |
| EPIC-20 | `planning/epics/EPIC-20-payment-lifecycle-fsm.md` | in-progress | SPRZECZNE z frozen finality; tylko cztery statusy |
| EPIC-21 | `planning/epics/EPIC-21-iso-identifier-refactor.md` | done | POTWIERDZONE dla thin scope |
| EPIC-22 | `planning/epics/EPIC-22-identity-time-crosscutting.md` | done | POTWIERDZONE — ClockPort/identity seams |
| EPIC-23 | `planning/epics/EPIC-23-frontend-foundation.md` | in-progress | CZĘŚCIOWO POTWIERDZONE |
| EPIC-24 | `planning/epics/EPIC-24-frontend-screens.md` | in-progress | CZĘŚCIOWO POTWIERDZONE — payments only, operations absent |
| EPIC-25 | `planning/epics/EPIC-25-observability-consolidation.md` | in-progress | CZĘŚCIOWO POTWIERDZONE |
| EPIC-26 | `planning/epics/EPIC-26-iso-message-lineage-core.md` | in-progress | CZĘŚCIOWO POTWIERDZONE — good tables, incomplete message set |
| EPIC-27 | `planning/epics/EPIC-27-iso-correlation-engine.md` | in-progress | CZĘŚCIOWO POTWIERDZONE — pacs.002 parser/correlation, no real CSM ingress |
| EPIC-28 | `planning/epics/EPIC-28-iso-validation-boundaries.md` | in-progress | CZĘŚCIOWO POTWIERDZONE — parser hardening, no EPC/CSM profiles |
| EPIC-29 | `planning/epics/EPIC-29-iso-outbound-artifact-lineage.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-30 | `planning/epics/EPIC-30-iso-r-message-lineage.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-31 | `planning/epics/EPIC-31-signature-module.md` | done | POTWIERDZONE dla signature evidence scope |
| EPIC-32 | `planning/epics/EPIC-32-ledger-core.md` | in-progress | CZĘŚCIOWO POTWIERDZONE DDL; CRITICAL currency/FK/reversal defects |
| EPIC-33 | `planning/epics/EPIC-33-instant-settlement.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-34 | `planning/epics/EPIC-34-deferred-settlement.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-35 | `planning/epics/EPIC-35-settlement-strategy-resolver.md` | in-progress | CZĘŚCIOWO POTWIERDZONE resolver-only |
| EPIC-36 | `planning/epics/EPIC-36-settlement-gross-instant.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-37 | `planning/epics/EPIC-37-settlement-deferred-net-cycles.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-38 | `planning/epics/EPIC-38-settlement-internal-book-file-batch.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-39 | `planning/epics/EPIC-39-settlement-finality-model.md` | not-started | TYLKO ZAPROJEKTOWANE; prerequisite dla bezpiecznego flow |
| EPIC-40 | `planning/epics/EPIC-40-settlement-insufficient-liquidity.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-41 | `planning/epics/EPIC-41-settlement-cgs-prefunded.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-42 | `planning/epics/EPIC-42-settlement-return-vs-reversal.md` | not-started | TYLKO ZAPROJEKTOWANE, lecz poprawna frozen semantyka |
| EPIC-43 | `planning/epics/EPIC-43-egress-rail-outbound-dispatch.md` | in-progress | CZĘŚCIOWO POTWIERDZONE skeleton; runtime datasource/RLS broken |
| EPIC-44 | `planning/epics/EPIC-44-egress-profile-artifact-taxonomy.md` | not-started | SPRZECZNE z indexem; design/decision open |
| EPIC-45 | `planning/epics/EPIC-45-egress-outbound-message-lifecycle.md` | not-started | SPRZECZNE z indexem; częściowe DDL |
| EPIC-46 | `planning/epics/EPIC-46-egress-delivery-attempts-retry.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-47 | `planning/epics/EPIC-47-egress-delivery-receipts-five-status.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-48 | `planning/epics/EPIC-48-egress-batch-result-file-delivery.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-49 | `planning/epics/EPIC-49-egress-case-r-message-outbound.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-50 | `planning/epics/EPIC-50-egress-observability-test-lab.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-51 | `planning/epics/EPIC-51-routing-candidate-profile-resolution.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-52 | `planning/epics/EPIC-52-routing-eligibility-reachability.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-53 | `planning/epics/EPIC-53-routing-decision-explanation.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-54 | `planning/epics/EPIC-54-routing-fallback-rules.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-55 | `planning/epics/EPIC-55-routing-cutoff-cycle-liquidity-precheck.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-56 | `planning/epics/EPIC-56-routing-test-lab.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-57 | `planning/epics/EPIC-57-reconciliation-profiles-snapshot.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-58 | `planning/epics/EPIC-58-reconciliation-evidence-collection.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-59 | `planning/epics/EPIC-59-reconciliation-settlement-vs-ledger.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-60 | `planning/epics/EPIC-60-reconciliation-balance-drift-detection.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-61 | `planning/epics/EPIC-61-reconciliation-mismatch-taxonomy.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-62 | `planning/epics/EPIC-62-reconciliation-exception-lifecycle.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-63 | `planning/epics/EPIC-63-reconciliation-egress-iso-case-recon.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-64 | `planning/epics/EPIC-64-reconciliation-observability-test-lab.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-65 | `planning/epics/EPIC-65-case-r-message-catalog.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-66 | `planning/epics/EPIC-66-case-reject-return-recall-rules.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-67 | `planning/epics/EPIC-67-case-evidence-bundles.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-68 | `planning/epics/EPIC-68-case-decisions-action-requests.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-69 | `planning/epics/EPIC-69-case-egress-iso-outbound-responses.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-70 | `planning/epics/EPIC-70-case-duplicate-conflicting-r-messages.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-71 | `planning/epics/EPIC-71-case-reconciliation-linked-cases.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-72 | `planning/epics/EPIC-72-case-observability-test-lab.md` | not-started | TYLKO ZAPROJEKTOWANE; claims tylko coarse P1/P2 |
| EPIC-73 | `planning/epics/EPIC-73-ingress-file-rail.md` | not-started | TYLKO ZAPROJEKTOWANE |
| EPIC-74 | `planning/epics/EPIC-74-keycloak-security-adoption.md` | not-started | TYLKO ZAPROJEKTOWANE; target 12 ról |
| EPIC-75 | `planning/epics/EPIC-75-documentation-hygiene.md` | done | POTWIERDZONE — governance/docs only |

## A.4. Statystyki i jakość stories

| Zakres | Done | In progress | Blocked | Not started | Razem |
|---|---:|---:|---:|---:|---:|
| EPIC | 15 | 14 | 4 | 43 | 76 |
| Story | 85 | 0 | 30 | 164 | 279 |

F0 ma 29/29 done; ownership 17 done/7 blocked/12 not-started; spine/frontend 18 done/18 blocked; ISO 9 done/5 blocked/9 not-started; signature 4/4; ledger/settlement 4 done/32 not-started; egress 3 done/25 not-started; routing 0/24; reconciliation 0/27; case 0/26; file/security/docs 1 done/9 not-started. 276/279 stories ma `verify:`, ale tylko 98 ma jawne `Opis:`, 50 jawne `Kryterium ukończenia`, 0/76 strukturalnego business owner i 0 zewnętrznego rulebook URL w `source:`.

## A.5. Rejestr sprzeczności artefaktów

| ID | Artefakty | Sprzeczność | Werdykt |
|---|---|---|---|
| CON-001 | `planning/README.md` vs EPIC-14/44/45 frontmatter | index: in-progress; frontmatter: not-started | Naprawić parity, bez zmiany merytorycznego scope |
| CON-002 | capability graph vs inventory | EPIC-14.14.2 not-started vs done | Validator nie sprawdza status parity |
| CON-003 | `PaymentTransitionTable`/V20 vs frozen ADR | `DISPATCHED` terminal i `is_final=true` | **Rozwiązane 2026-07-20:** V30 czyści false-positive dane, recorder nie wyprowadza finality z topologii FSM; pełna settlement-owned authority pozostaje open |
| CON-004 | AsyncAPI vs outbox code | `payment.received/validated` ownership nie odpowiada `payment.submitted.v1` i publikacji na `payment.validated` | Naprawić kontrakt przed dalszym routingiem |
| CON-005 | grants vs Spring wiring | role dispatcherów istnieją w SQL, aplikacja używa `sepa_app` | Implementacja nieoperacyjna mimo testów algorytmu |
| CON-006 | target IAM vs realm | blueprint 12 ról, export 4 role | Jawny etap nieukończony, nie „done security” |
| CON-007 | dokument full-blueprint | 16/16 flows opisane | Jest to coverage projektu, nie implementacji |

## A.6. Ograniczenie

Repozytorium było celowo zmienione przed audytem. Stan został odczytany bez cofania zmian; nowe, niezatwierdzone V28 i klasy egress/ledger są traktowane jako bieżący working tree, z flagą niższej stabilności. Daty artefaktów są oparte na zawartości/frontmatter i dacie audytu, nie na domniemanym formalnym release management.

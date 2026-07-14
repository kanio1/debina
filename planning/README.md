# Planning Index — SEPA Nexus

Katalog epików/stories/tasków wyprowadzony z dokumentacji projektu (`/home/suso/debina/*.md`) zgodnie ze skillami `artifact-derived-planning` i `epic-story-task-catalog`. **Nic tu nie jest wymyślone** — każdy epik ma pole `source` wskazujące dokładny plik/sekcję, z której wynika jego zakres. Priorytety (`[MVP]`/`[P1]`/`[P2]`) i zależności są tymi już ustalonymi w dokumentacji (ADR-N6 — jedna taksonomia), nie nową priorytetyzacją.

**76 epików, 266 stories, 319 tasków.** Zakres nie został z góry ograniczony do jednej iteracji — obejmuje całość tego, co dokumentacja projektu opisuje jako nazwane epiki (Iteracja 0 → MVP Iteracje 1-5 → fala P1 → część P2).

## Jak z tego korzystać (nowa sesja)

1. Zacznij od `HANDOFF.md` w korzeniu repo (jeśli istnieje).
2. Znajdź pierwszy epik w statusie `not-started`/`in-progress` idąc **od góry tabeli w dół** — kolejność w tabeli już odzwierciedla zależności techniczne (`depends_on`). Nie przeskakuj epików z niespełnionymi zależnościami.
3. W pliku epika: pracuj story po story, task po tasku. Odhacz checkbox dopiero gdy `verify:` faktycznie przejdzie.
4. Zaktualizuj `status` we frontmatterze story/epika, gdy wszystkie jego taski/story są odhaczone — checkbox i `status` muszą być spójne (patrz skill `epic-story-task-catalog`).

## Faza 0 — Platform Skeleton (Iteracja 0, `sepa-nexus-iteration-0-foundation-plan.md`)

`[NO-PLAYWRIGHT]` obowiązuje w całej tej fazie.

| Epik | Status | Zależy od | Plik |
|---|---|---|---|
| EPIC-00 — Repository & Agent Foundation | **done** | — | [epics/EPIC-00-repository-agent-foundation.md](epics/EPIC-00-repository-agent-foundation.md) |
| EPIC-01 — PostgreSQL 18 Foundation | **done** | EPIC-00 | [epics/EPIC-01-postgresql-foundation.md](epics/EPIC-01-postgresql-foundation.md) |
| EPIC-02 — Keycloak 26.6.4 Realm (4 role) | **done** | EPIC-00 | [epics/EPIC-02-keycloak-realm-iteration-0.md](epics/EPIC-02-keycloak-realm-iteration-0.md) |
| EPIC-03 — Spring Boot/Modulith Backend Skeleton | **done** | EPIC-00, EPIC-01 | [epics/EPIC-03-spring-modulith-backend-skeleton.md](epics/EPIC-03-spring-modulith-backend-skeleton.md) |
| EPIC-04 — Outbox/Inbox + Kafka (cienkie) | **done** | EPIC-01, EPIC-03 | [epics/EPIC-04-outbox-inbox-kafka-thin.md](epics/EPIC-04-outbox-inbox-kafka-thin.md) |
| EPIC-05 — Next.js BFF | not-started | EPIC-02, EPIC-03 | [epics/EPIC-05-nextjs-bff.md](epics/EPIC-05-nextjs-bff.md) |
| EPIC-06 — React/shadcn Frontend Skeleton | not-started | EPIC-05 | [epics/EPIC-06-react-shadcn-frontend-skeleton.md](epics/EPIC-06-react-shadcn-frontend-skeleton.md) |
| EPIC-07 — CI/CD Foundation | not-started | EPIC-03, EPIC-06 | [epics/EPIC-07-ci-cd-foundation.md](epics/EPIC-07-ci-cd-foundation.md) |
| EPIC-08 — Walking Skeleton Verification | not-started | EPIC-04, EPIC-05, EPIC-06, EPIC-07 | [epics/EPIC-08-walking-skeleton-verification.md](epics/EPIC-08-walking-skeleton-verification.md) |

## Faza 1 — Ownership / Architecture Enforcement (cross-cutting)

| Epik | Status | Zależy od | Plik |
|---|---|---|---|
| EPIC-09 — Ownership: schema/grant enforcement | not-started | EPIC-03, EPIC-07 | [epics/EPIC-09-ownership-schema-grants.md](epics/EPIC-09-ownership-schema-grants.md) |
| EPIC-10 — Ownership: ISO lineage split | not-started | EPIC-09, EPIC-26, EPIC-21 | [epics/EPIC-10-iso-lineage-ownership.md](epics/EPIC-10-iso-lineage-ownership.md) |
| EPIC-11 — Ownership: cienki wiersz payments | not-started | EPIC-09, EPIC-20 | [epics/EPIC-11-payment-slim-ownership.md](epics/EPIC-11-payment-slim-ownership.md) |
| EPIC-12 — Ownership: katalogi reference-data | not-started | EPIC-09 | [epics/EPIC-12-reference-data-ownership.md](epics/EPIC-12-reference-data-ownership.md) |
| EPIC-13 — Ownership: ledger | not-started | EPIC-09, EPIC-32 | [epics/EPIC-13-ledger-ownership.md](epics/EPIC-13-ledger-ownership.md) |
| EPIC-14 — Ownership: granica egress | not-started | EPIC-09, EPIC-43 | [epics/EPIC-14-egress-boundary-ownership.md](epics/EPIC-14-egress-boundary-ownership.md) |
| EPIC-15 — Ownership: topiki Kafka | not-started | EPIC-09, EPIC-04 | [epics/EPIC-15-kafka-topic-ownership.md](epics/EPIC-15-kafka-topic-ownership.md) |
| EPIC-16 — Ownership: read modele/GraphQL read-only | not-started | EPIC-09 | [epics/EPIC-16-read-model-graphql-ownership.md](epics/EPIC-16-read-model-graphql-ownership.md) |
| EPIC-17 — Ownership: wymuszenie ścieżki symulacji | not-started | EPIC-09 | [epics/EPIC-17-simulation-path-enforcement.md](epics/EPIC-17-simulation-path-enforcement.md) |
| EPIC-18 — Ownership: rollout outbox/inbox (pozostałe moduły) | not-started | EPIC-09, EPIC-04 | [epics/EPIC-18-per-schema-outbox-inbox-rollout.md](epics/EPIC-18-per-schema-outbox-inbox-rollout.md) |

## Faza 2 — Payment Spine (Iteracja 1)

| Epik | Status | Zależy od | Plik |
|---|---|---|---|
| EPIC-19 — Ingress: staging pipeline | not-started | EPIC-08 | [epics/EPIC-19-ingress-staging-pipeline.md](epics/EPIC-19-ingress-staging-pipeline.md) |
| EPIC-20 — Payment Lifecycle: FSM | not-started | EPIC-19 | [epics/EPIC-20-payment-lifecycle-fsm.md](epics/EPIC-20-payment-lifecycle-fsm.md) |
| EPIC-21 — Refaktor identyfikatorów ISO | not-started | EPIC-19 | [epics/EPIC-21-iso-identifier-refactor.md](epics/EPIC-21-iso-identifier-refactor.md) |
| EPIC-22 — Tożsamość i czas: cross-cutting | not-started | EPIC-02, EPIC-03 | [epics/EPIC-22-identity-time-crosscutting.md](epics/EPIC-22-identity-time-crosscutting.md) |
| EPIC-23 — Frontend Foundation | not-started | EPIC-05, EPIC-06 | [epics/EPIC-23-frontend-foundation.md](epics/EPIC-23-frontend-foundation.md) |
| EPIC-24 — Frontend Screens (**pierwsze testy Playwright tutaj**) | not-started | EPIC-23 | [epics/EPIC-24-frontend-screens.md](epics/EPIC-24-frontend-screens.md) |
| EPIC-25 — Konsolidacja obserwowalności | not-started | EPIC-07 | [epics/EPIC-25-observability-consolidation.md](epics/EPIC-25-observability-consolidation.md) |

## Faza 3 — ISO lineage/korelacja (Iteracja 1-5)

| Epik | Status | Zależy od | Plik |
|---|---|---|---|
| EPIC-26 — ISO: rdzeń lineage wiadomości | not-started | EPIC-19 | [epics/EPIC-26-iso-message-lineage-core.md](epics/EPIC-26-iso-message-lineage-core.md) |
| EPIC-27 — ISO: silnik korelacji | not-started | EPIC-26 | [epics/EPIC-27-iso-correlation-engine.md](epics/EPIC-27-iso-correlation-engine.md) |
| EPIC-28 — ISO: granice walidacji | not-started | EPIC-19 | [epics/EPIC-28-iso-validation-boundaries.md](epics/EPIC-28-iso-validation-boundaries.md) |
| EPIC-29 — ISO: lineage artefaktów wychodzących `[P1]` | not-started | EPIC-44 | [epics/EPIC-29-iso-outbound-artifact-lineage.md](epics/EPIC-29-iso-outbound-artifact-lineage.md) |
| EPIC-30 — ISO: lineage R-message `[P1]` | not-started | EPIC-65 | [epics/EPIC-30-iso-r-message-lineage.md](epics/EPIC-30-iso-r-message-lineage.md) |

## Faza 4 — Signature

| Epik | Status | Zależy od | Plik |
|---|---|---|---|
| EPIC-31 — Moduł Signature | not-started | EPIC-09 | [epics/EPIC-31-signature-module.md](epics/EPIC-31-signature-module.md) |

## Faza 5 — Ledger / Settlement (Iteracja 2/4/5)

| Epik | Status | Zależy od | Plik |
|---|---|---|---|
| EPIC-32 — Ledger | not-started | EPIC-09 | [epics/EPIC-32-ledger-core.md](epics/EPIC-32-ledger-core.md) |
| EPIC-33 — Rozliczenie natychmiastowe | not-started | EPIC-32, EPIC-35 | [epics/EPIC-33-instant-settlement.md](epics/EPIC-33-instant-settlement.md) |
| EPIC-34 — Rozliczenie odroczone | not-started | EPIC-32, EPIC-37 | [epics/EPIC-34-deferred-settlement.md](epics/EPIC-34-deferred-settlement.md) |
| EPIC-35 — Settlement: resolver strategii | not-started | EPIC-12 | [epics/EPIC-35-settlement-strategy-resolver.md](epics/EPIC-35-settlement-strategy-resolver.md) |
| EPIC-36 — Settlement: gross instant + LedgerPort | not-started | EPIC-35, EPIC-13 | [epics/EPIC-36-settlement-gross-instant.md](epics/EPIC-36-settlement-gross-instant.md) |
| EPIC-37 — Settlement: netting odroczony i cykle | not-started | EPIC-35 | [epics/EPIC-37-settlement-deferred-net-cycles.md](epics/EPIC-37-settlement-deferred-net-cycles.md) |
| EPIC-38 — Settlement: book wewnętrzny i wsad plikowy | not-started | EPIC-35 | [epics/EPIC-38-settlement-internal-book-file-batch.md](epics/EPIC-38-settlement-internal-book-file-batch.md) |
| EPIC-39 — Settlement: model finalności | not-started | EPIC-35 | [epics/EPIC-39-settlement-finality-model.md](epics/EPIC-39-settlement-finality-model.md) |
| EPIC-40 — Settlement: niewystarczająca płynność | not-started | EPIC-35 | [epics/EPIC-40-settlement-insufficient-liquidity.md](epics/EPIC-40-settlement-insufficient-liquidity.md) |
| EPIC-41 — Settlement: CGS i prefunded `[P1]` | not-started | EPIC-35 | [epics/EPIC-41-settlement-cgs-prefunded.md](epics/EPIC-41-settlement-cgs-prefunded.md) |
| EPIC-42 — Settlement: zwrot vs reversal | not-started | EPIC-39 | [epics/EPIC-42-settlement-return-vs-reversal.md](epics/EPIC-42-settlement-return-vs-reversal.md) |

## Faza 6 — Egress / Outbound (Iteracja 2)

| Epik | Status | Zależy od | Plik |
|---|---|---|---|
| EPIC-43 — Egress: szyna wychodząca | not-started | EPIC-09 | [epics/EPIC-43-egress-rail-outbound-dispatch.md](epics/EPIC-43-egress-rail-outbound-dispatch.md) |
| EPIC-44 — Egress: profil i taksonomia artefaktów | not-started | EPIC-43 | [epics/EPIC-44-egress-profile-artifact-taxonomy.md](epics/EPIC-44-egress-profile-artifact-taxonomy.md) |
| EPIC-45 — Egress: cykl życia wiadomości wychodzącej | not-started | EPIC-44 | [epics/EPIC-45-egress-outbound-message-lifecycle.md](epics/EPIC-45-egress-outbound-message-lifecycle.md) |
| EPIC-46 — Egress: próby dostawy i retry | not-started | EPIC-45 | [epics/EPIC-46-egress-delivery-attempts-retry.md](epics/EPIC-46-egress-delivery-attempts-retry.md) |
| EPIC-47 — Egress: potwierdzenia dostawy, 5-osiowy status | not-started | EPIC-46 | [epics/EPIC-47-egress-delivery-receipts-five-status.md](epics/EPIC-47-egress-delivery-receipts-five-status.md) |
| EPIC-48 — Egress: dostawa pliku wynikowego | not-started | EPIC-43 | [epics/EPIC-48-egress-batch-result-file-delivery.md](epics/EPIC-48-egress-batch-result-file-delivery.md) |
| EPIC-49 — Egress: wychodzące case/R-message `[P1]` | not-started | EPIC-65 | [epics/EPIC-49-egress-case-r-message-outbound.md](epics/EPIC-49-egress-case-r-message-outbound.md) |
| EPIC-50 — Egress: obserwowalność i test lab | not-started | EPIC-43 | [epics/EPIC-50-egress-observability-test-lab.md](epics/EPIC-50-egress-observability-test-lab.md) |

## Faza 7 — Routing (Iteracja 5)

| Epik | Status | Zależy od | Plik |
|---|---|---|---|
| EPIC-51 — Routing: rozwiązywanie kandydatów | not-started | EPIC-12 | [epics/EPIC-51-routing-candidate-profile-resolution.md](epics/EPIC-51-routing-candidate-profile-resolution.md) |
| EPIC-52 — Routing: kwalifikowalność i osiągalność | not-started | EPIC-51 | [epics/EPIC-52-routing-eligibility-reachability.md](epics/EPIC-52-routing-eligibility-reachability.md) |
| EPIC-53 — Routing: decyzja i wyjaśnienie | not-started | EPIC-52 | [epics/EPIC-53-routing-decision-explanation.md](epics/EPIC-53-routing-decision-explanation.md) |
| EPIC-54 — Routing: reguły fallback | not-started | EPIC-53 | [epics/EPIC-54-routing-fallback-rules.md](epics/EPIC-54-routing-fallback-rules.md) |
| EPIC-55 — Routing: precheck cutoff/cykl/płynność | not-started | EPIC-53, EPIC-37 | [epics/EPIC-55-routing-cutoff-cycle-liquidity-precheck.md](epics/EPIC-55-routing-cutoff-cycle-liquidity-precheck.md) |
| EPIC-56 — Routing: test lab | not-started | EPIC-53, EPIC-54 | [epics/EPIC-56-routing-test-lab.md](epics/EPIC-56-routing-test-lab.md) |

## Faza 8 — Reconciliation (Iteracja 4)

| Epik | Status | Zależy od | Plik |
|---|---|---|---|
| EPIC-57 — Reconciliation: profile i snapshot | not-started | EPIC-12 | [epics/EPIC-57-reconciliation-profiles-snapshot.md](epics/EPIC-57-reconciliation-profiles-snapshot.md) |
| EPIC-58 — Reconciliation: zbieranie evidence | not-started | EPIC-57 | [epics/EPIC-58-reconciliation-evidence-collection.md](epics/EPIC-58-reconciliation-evidence-collection.md) |
| EPIC-59 — Reconciliation: settlement vs ledger | not-started | EPIC-58 | [epics/EPIC-59-reconciliation-settlement-vs-ledger.md](epics/EPIC-59-reconciliation-settlement-vs-ledger.md) |
| EPIC-60 — Reconciliation: dryf salda | not-started | EPIC-58 | [epics/EPIC-60-reconciliation-balance-drift-detection.md](epics/EPIC-60-reconciliation-balance-drift-detection.md) |
| EPIC-61 — Reconciliation: taksonomia mismatch | not-started | EPIC-59, EPIC-60 | [epics/EPIC-61-reconciliation-mismatch-taxonomy.md](epics/EPIC-61-reconciliation-mismatch-taxonomy.md) |
| EPIC-62 — Reconciliation: cykl życia wyjątku | not-started | EPIC-61 | [epics/EPIC-62-reconciliation-exception-lifecycle.md](epics/EPIC-62-reconciliation-exception-lifecycle.md) |
| EPIC-63 — Reconciliation: egress/ISO/case `[P1]` | not-started | EPIC-58, EPIC-65 | [epics/EPIC-63-reconciliation-egress-iso-case-recon.md](epics/EPIC-63-reconciliation-egress-iso-case-recon.md) |
| EPIC-64 — Reconciliation: obserwowalność i test lab | not-started | EPIC-57 | [epics/EPIC-64-reconciliation-observability-test-lab.md](epics/EPIC-64-reconciliation-observability-test-lab.md) |

## Faza 9 — Case module (`[P1]`)

| Epik | Status | Zależy od | Plik |
|---|---|---|---|
| EPIC-65 — Case: katalog R-message | not-started | EPIC-09, EPIC-27 | [epics/EPIC-65-case-r-message-catalog.md](epics/EPIC-65-case-r-message-catalog.md) |
| EPIC-66 — Case: reguły reject/return/recall | not-started | EPIC-65, EPIC-42 | [epics/EPIC-66-case-reject-return-recall-rules.md](epics/EPIC-66-case-reject-return-recall-rules.md) |
| EPIC-67 — Case: bundle evidence | not-started | EPIC-65 | [epics/EPIC-67-case-evidence-bundles.md](epics/EPIC-67-case-evidence-bundles.md) |
| EPIC-68 — Case: decyzje i action requests | not-started | EPIC-66 | [epics/EPIC-68-case-decisions-action-requests.md](epics/EPIC-68-case-decisions-action-requests.md) |
| EPIC-69 — Case: odpowiedzi wychodzące egress/ISO | not-started | EPIC-49 | [epics/EPIC-69-case-egress-iso-outbound-responses.md](epics/EPIC-69-case-egress-iso-outbound-responses.md) |
| EPIC-70 — Case: duplikaty i konflikty R-message | not-started | EPIC-65 | [epics/EPIC-70-case-duplicate-conflicting-r-messages.md](epics/EPIC-70-case-duplicate-conflicting-r-messages.md) |
| EPIC-71 — Case: przypadki powiązane z rekoncyliacją | not-started | EPIC-65, EPIC-62 | [epics/EPIC-71-case-reconciliation-linked-cases.md](epics/EPIC-71-case-reconciliation-linked-cases.md) |
| EPIC-72 — Case: obserwowalność i test lab | not-started | EPIC-65 | [epics/EPIC-72-case-observability-test-lab.md](epics/EPIC-72-case-observability-test-lab.md) |

## Faza 10 — Plik-rail, bezpieczeństwo pełne, higiena dokumentacji

| Epik | Status | Zależy od | Plik |
|---|---|---|---|
| EPIC-73 — Ingress: szyna plikowa | not-started | EPIC-19 | [epics/EPIC-73-ingress-file-rail.md](epics/EPIC-73-ingress-file-rail.md) |
| EPIC-74 — Adopcja Keycloak (pełny model, poza Iterację 0) | not-started | EPIC-02 | [epics/EPIC-74-keycloak-security-adoption.md](epics/EPIC-74-keycloak-security-adoption.md) |
| EPIC-75 — Higiena dokumentacji | **done** | — | [epics/EPIC-75-documentation-hygiene.md](epics/EPIC-75-documentation-hygiene.md) |

---

## Otwarte pytania zebrane z całego katalogu (`[OPEN-QUESTION]`)

Żadne z poniższych nie zostało rozstrzygnięte samodzielnie — zgodnie ze skillem `artifact-derived-planning`, brak odpowiedzi w dokumentacji jest tu zapisany jako pytanie, nie jako decyzja podjęta w locie. Szczegóły przy każdym punkcie w odpowiednim pliku epika.

1. **EPIC-00** — Iteracja 0 wg ADR-N1/decision gate obejmuje OTel w stosie compose, ale konkretny `docker-compose.yml` w `sepa-nexus-iteration-0-foundation-plan.md` go nie zawiera.
2. **EPIC-02 / EPIC-74** — "4 role" w Iteracji 0 (IT0, dokument wizji Playwright) vs "11/12 ról" jako jedyne liczby podane w obu blueprintach Keycloak — brak sprzeczności blokującej, ale warto mieć odnotowane.
3. **EPIC-12** — `EPIC-OWN-4` w wersji MFB §8 vs OWN §9: OWN §9 pomija `settlement_cutoff_calendar` w swojej liście, mimo że OWN §10 potwierdza jej przynależność do `reference-data`. Traktowane jako niekompletne powtórzenie, nie zmiana zakresu.
4. **EPIC-17 / EPIC-24 (Simulation Lab)** — brak w źródłowym backlogu odrębnej rodziny epików dla samego silnika modułu `simulation` (analogicznej do EPIC-CASE/ROUTE/SETTLE), mimo że `simulation` jest `[MVP]` Iteracja 3 i "demo, które sprzedaje projekt". EPIC-17 pokrywa tylko egzekwowanie granic.
5. **EPIC-24** — rozbieżność między dokumentem wizji Playwright §6 (sugeruje Playwright możliwy już w ramach "walking skeleton") a component-foundation §9 krok 8→9 i IT0 `[NO-PLAYWRIGHT]` (Playwright dopiero po pierwszych 3 ekranach). Przyjęto drugą, bardziej konkretną i późniejszą regułę jako obowiązującą.
6. **EPIC-24** — umiejscowienie `operatorWorklist` (S-01): "leaning Control Room tab" powtórzone w trzech dokumentach UI-spec jako wciąż nierozstrzygnięte.
7. **EPIC-24** — format eksportu i reguły PII-gating bundle'a Evidence jawnie oznaczone jako "wymaga decyzji governance przed buildem" (`sepa-nexus-final-3-screens-ui-spec.md` §11).
8. **EPIC-31** — dwie tożsamości epika dla modułu signature w źródłach (`EPIC-SIG` vs `EPIC-OWN-10`), połączone tu w jeden plik jako decyzja porządkowa tej konsolidacji.
9. **EPIC-31** — czy cienki wiersz werdyktu signature ląduje w Iteracji 1, jest jawnie warunkowe w źródle ("jeśli chcemy wczesnego demo").
10. **EPIC-74** — starszy blueprint Keycloak deklaruje się jako źródło prawdy dla modelu 11 ról, nowszy rewiduje do 12 (`payment_approver`) bez aktualizacji starszego dokumentu.
11. **Poza katalogiem** — dwie regulacyjno-podobne pozycje nigdy nie rozstrzygnięte w żadnym kanonicznym dokumencie: polityka retencji danych i granica PII/GDPR (oznaczanie danych syntetycznych jako "personal"). Nie mają jeszcze własnego epika — do zaadresowania przy najbliższym epiku dotykającym Evidence/Audit (EPIC-24 Story 24.8) lub Reference Data (EPIC-12), zgodnie z sugestią `sepa-nexus-prior-thread-files-analysis.md`.

## Ograniczenia tej konsolidacji

- Komendy `verify:` dla epików poza Fazą 0 (Iteracja 0) odwołują się do konwencji ustalonych w Iteracji 0 (`./mvnw -f backend test -Dtest=<Nazwa>`, `npm run test:e2e`) i nazw testów/artefaktów wskazanych w dokumentacji źródłowej — ale same klasy testowe/pliki jeszcze nie istnieją (projekt jest pre-code). Komenda stanie się wykonywalna dopiero gdy dany kod powstanie; do tego czasu jest specyfikacją oczekiwanego zachowania, nie potwierdzonym faktem.
- Część epików dzieli dokładnie ten sam task/test między dwoma plikami (np. `CycleCloseRaceTest` w EPIC-34 i EPIC-37) — taka sytuacja jest odnotowana w treści jako "współdzielony", zgodnie z tym, jak same dokumenty źródłowe opisują tę samą regułę z dwóch różnych epików.

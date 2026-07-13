---
status: not-started
depends_on: [EPIC-07-ci-cd-foundation]
source: "sepa-nexus-full-blueprint-review-and-task-plan.md line 354 (EPIC-OBS-1) + §17 (linie 463-481) + R-17 (linia 208, linia 317)"
---

# EPIC-25 — Konsolidacja obserwowalności (EPIC-OBS-1)

Inwentarz w §17. R-17 traktuje samo zaprojektowanie inwentarza jako zadanie Iteracji 0/1; konkretny rollout metryk jest rozłożony Iteracja 1→4 (poniżej). Ten epik nie ma w źródle podziału na story-ID (`OBS-S1` itp.) — mniej sformalizowany niż EPIC-SIG/EPIC-SEC-KC.

## Story 25.1 — Inwentarz obserwowalności (Iteracja 0/1)

status: not-started
depends_on: []

Taski:
- [ ] **Spisz jeden inwentarz metryk/tracingu/logów wg §17 (ingress p95<300ms, span-per-stage, korelacja id).**
      `verify: test -f docs/observability-inventory.md` (lub równoważny artefakt w repo docelowym).

## Story 25.2 — Segmentowany budżet latencji (Iteracja 1-2)

status: not-started
depends_on: [Story 25.1, EPIC-19-ingress-staging-pipeline]

Opis: histogramy ingress→validate→route→settle→pacs002→e2e, jedna ciągła trasa (trace), alert na przekroczenie p95 e2e.

Taski:
- [ ] **Wpięcie histogramów per etap + jednej ciągłej trasy trace + alertu na p95 e2e.**
      `verify: ./mvnw -f backend test -Dtest=*SegmentedLatencyMetricsTest*`

## Story 25.3 — Lag Kafka + tablica DLQ (Iteracja 3)

status: not-started
depends_on: [Story 25.1, EPIC-04-outbox-inbox-kafka-thin]

Taski:
- [ ] **Metryka lag-per-consumer-group jako obywatel pierwszej klasy, licznik retry, głębokość DLQ, propagacja nagłówków, alert na DLQ>0 dla `csm.response`/topiców rekoncyliacji.**
      `verify: ./mvnw -f backend test -Dtest=*KafkaLagDlqBoardTest*`

## Story 25.4 — Reguły alertów (Iteracja 4)

status: not-started
depends_on: [Story 25.2, Story 25.3]

Opis: generalizacja alertów per-obszar już wskazanych w §17 (illegal-transition>0, drift≠0=CRITICAL, cycle-close-overdue, dead-letter>0/status-out>5s, CRITICAL-exception-immediate, expired-cases, audit-write-failure=page, queue-depth-thresholds).

Taski:
- [ ] **Wdroż ogólną warstwę reguł alertów ponad metrykami z 25.2/25.3, pokrywającą listę z §17.**
      `verify: ./mvnw -f backend test -Dtest=*AlertRulesCoverageTest*`

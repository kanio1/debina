---
status: in-progress
depends_on: [EPIC-07-ci-cd-foundation]
source: "sepa-nexus-full-blueprint-review-and-task-plan.md line 354 (EPIC-OBS-1) + §17 (linie 463-481) + R-17 (linia 208, linia 317)"
---

# EPIC-25 — Konsolidacja obserwowalności (EPIC-OBS-1)

Inwentarz w §17. R-17 traktuje samo zaprojektowanie inwentarza jako zadanie Iteracji 0/1; konkretny rollout metryk jest rozłożony Iteracja 1→4 (poniżej). Ten epik nie ma w źródle podziału na story-ID (`OBS-S1` itp.) — mniej sformalizowany niż EPIC-SIG/EPIC-SEC-KC.

## Story 25.1 — Inwentarz obserwowalności (Iteracja 0/1)

status: done
depends_on: []

Taski:
- [x] **Spisz jeden inwentarz metryk/tracingu/logów wg §17 (ingress p95<300ms, span-per-stage, korelacja id).**
      `verify: test -f docs/observability-inventory.md` → PASS (2026-07-14). Nowy `docs/observability-inventory.md` — tabela §17 skopiowana dosłownie ze źródła (nie wymyślona), plus notatka o istniejącym dziś częściowym pokryciu (`CorrelationIdFilter` z EPIC-03 już dołącza `correlationId`, ale brak integracji OTel `traceId`/`paymentTraceId`) i o rzeczywistych blokerach dla Stories 25.2-25.4 (patrz niżej).

## Story 25.2 — Segmentowany budżet latencji (Iteracja 1-2)

status: blocked
depends_on: [Story 25.1, EPIC-19-ingress-staging-pipeline]

Opis: histogramy ingress→validate→route→settle→pacs002→e2e, jedna ciągła trasa (trace), alert na przekroczenie p95 e2e.

`[PLANNING-DEFECT 2026-07-14]`: task wymaga histogramów dla WSZYSTKICH etapów `ingress→validate→route→settle→pacs002→e2e` — dziś istnieje tylko `ingress` (EPIC-19 Story 19.1). `validate`/`route`/`settle`/`pacs002` to osobne, jeszcze nie zbudowane etapy (EPIC-20 Story 20.3, EPIC-27, EPIC-32+, EPIC-35+, wszystkie `not-started`/`blocked`). **Status `blocked`** — odblokuj, gdy pełny łańcuch etapów faktycznie istnieje.

Taski:
- [ ] **Wpięcie histogramów per etap + jednej ciągłej trasy trace + alertu na p95 e2e.**
      `verify: ./mvnw -f backend test -Dtest=*SegmentedLatencyMetricsTest*` — `NOT RUN`, `blocked` (patrz wyżej).

## Story 25.3A — Lag Kafka per consumer group (Iteracja 3)

status: **done**
depends_on: [Story 25.1, EPIC-04-outbox-inbox-kafka-thin]

`[SPLIT 2026-07-16 — dual-agent governance/backlog-redesign session, H5]`: was Story 25.3 ("Lag Kafka + tablica DLQ"), whose second task bundled retry-count metric, DLQ-depth metric, correlation-header propagation, and DLQ>0 alerting behind one checkbox/verify. Split into 25.3A (this story — lag, already done, unaffected) and four independently-blocked successors (25.3B–25.3E) below, so each concern can unblock on its own schedule instead of all four waiting on the slowest one.

Taski:
- [x] **Metryka lag-per-consumer-group jako obywatel pierwszej klasy.**
      `verify: ./mvnw -f backend test -Dtest=KafkaConsumerGroupLagGaugeTest` → `Tests run: 1, Failures: 0` — PASS (2026-07-14). Nowy `backend/src/main/java/com/sepanexus/modules/paymentlifecycle/event/KafkaConsumerGroupLagGauge.java` — gauge Micrometer `kafka.consumer.lag{group="payment-lifecycle-inbox",topic="payment.validated"}`, obliczany na żądanie przez `AdminClient` (`describeTopics`+`listConsumerGroupOffsets`+`listOffsets`, suma `max(0, endOffset-committedOffset)` po partycjach). `InboxConsumer`'s `@KafkaListener` otrzymał jawne `id` (potrzebne, by test mógł zatrzymać/wznowić dokładnie ten kontener). Nowy `KafkaConsumerGroupLagGaugeTest` — **nie-próżny dowód**: zatrzymuje kontener, produkuje realną wiadomość przez surowy `KafkaProducer` (Testcontainers Kafka), potwierdza `lag > 0`, wznawia kontener, potwierdza `lag` spada do `0` po realnej konsumpcji. `management.endpoints.web.exposure.include` rozszerzone o `metrics` (było tylko `health`) — bez tego metryka istniałaby, ale byłaby niewidoczna dla nikogo, sprzecznie z "obywatel pierwszej klasy". **Realny smoke-test przeciw żywemu stackowi**: `GET /actuator/metrics/kafka.consumer.lag` (z prawdziwym tokenem Keycloak — endpoint wymaga auth, tylko `health` jest publiczne) → `{"measurements":[{"statistic":"VALUE","value":0.0}], "availableTags":[{"tag":"topic","values":["payment.validated"]},{"tag":"group","values":["payment-lifecycle-inbox"]}]}` — realna wartość z żywej infrastruktury, nie mock.

## Story 25.3B — Licznik retry

status: blocked
depends_on: [Story 25.3A]

`[SPLIT 2026-07-16 — see Story 25.3A above]`. Blocked: no retry mechanism exists in `InboxConsumer` yet to count.

Taski:
- [ ] **Metryka liczby retry per consumer/topic.**
      `verify: ./mvnw -f backend test -Dtest=*KafkaRetryCounterTest*` — `NOT RUN`, `blocked`.

## Story 25.3C — Głębokość DLQ

status: blocked
depends_on: [Story 25.3A]

`[SPLIT 2026-07-16 — see Story 25.3A above]`. Blocked: no DLQ mechanism exists anywhere in this code (`InboxConsumer` has no dead-letter routing).

Taski:
- [ ] **Metryka głębokości DLQ per topic.**
      `verify: ./mvnw -f backend test -Dtest=*KafkaDlqDepthGaugeTest*` — `NOT RUN`, `blocked`.

## Story 25.3D — Propagacja nagłówków korelacyjnych

status: blocked
depends_on: [Story 25.3A]

`[SPLIT 2026-07-16 — see Story 25.3A above]`. Blocked: no correlation-header convention wired through consumer/producer yet.

Taski:
- [ ] **Nagłówki korelacyjne propagowane i widoczne w metrykach/logach.**
      `verify: ./mvnw -f backend test -Dtest=*CorrelationHeaderPropagationTest*` — `NOT RUN`, `blocked`.

## Story 25.3E — Alert na DLQ>0

status: blocked
depends_on: [Story 25.3C]

`[SPLIT 2026-07-16 — see Story 25.3A above]`. Blocked: depends on 25.3C's DLQ-depth metric existing; `csm.response`/reconciliation topics it would alert on target the `simulation`/`reconciliation` modules, both zero code.

Taski:
- [ ] **Alert na DLQ>0 dla `csm.response`/topiców rekoncyliacji.**
      `verify: ./mvnw -f backend test -Dtest=*DlqAlertRuleTest*` — `NOT RUN`, `blocked`.

## Story 25.4 — Reguły alertów (Iteracja 4)

status: blocked
depends_on: [Story 25.2, Story 25.3D, Story 25.3E]

`[NARROWED 2026-07-16]`: was `[Story 25.2, Story 25.3]` — repointed at the two specific successor stories from the 25.3 split (25.3D header propagation, 25.3E DLQ alert) this generalized alert-rule set actually builds on, instead of the whole pre-split 25.3.

Opis: generalizacja alertów per-obszar już wskazanych w §17 (illegal-transition>0, drift≠0=CRITICAL, cycle-close-overdue, dead-letter>0/status-out>5s, CRITICAL-exception-immediate, expired-cases, audit-write-failure=page, queue-depth-thresholds).

`depends_on` już wskazuje realny bloker — wszystkie trzy `blocked`. **Status `blocked`** transitively.

Taski:
- [ ] **Wdroż ogólną warstwę reguł alertów ponad metrykami z 25.2/25.3, pokrywającą listę z §17.**
      `verify: ./mvnw -f backend test -Dtest=*AlertRulesCoverageTest*` — `NOT RUN`, `blocked` (patrz wyżej).

# Observability Inventory — EPIC-25 Story 25.1

Source of truth: `sepa-nexus-full-blueprint-review-and-task-plan.md` §17 ("Observability and Operations Review"). This inventory is copied verbatim from that section — it is a design/planning artifact (per R-17, designing the inventory is Iteration 0/1 scope; the concrete metrics/tracing/alert rollout is spread across Iterations 1→4, tracked as EPIC-25 Stories 25.2–25.4). Nothing in this file is implemented yet unless explicitly noted.

## Correlation model

`[ADOPT]`: `traceId` (OTel) · `paymentTraceId` · `correlationId`; entity ids (`payment`/`file`/`attempt`/`journal`/`case`/`run`/`artifact`) on every log line and event where present.

`[PARTIAL, already true today]`: `com.sepanexus.modules.paymentlifecycle.web.CorrelationIdFilter` already attaches a `correlationId` to every request (EPIC-03) and `PaymentProblemHandler` echoes it on RFC 7807 error bodies. No OTel `traceId`/`paymentTraceId` integration exists yet.

## Area-by-area inventory (§17, verbatim)

| Area | Logs | Metrics | Traces | Dashboard | Alert | Gap? |
|---|---|---|---|---|---|---|
| Ingress | structured, ids, verdicts | accept p95 (<300ms), rejects by reason, file throughput | span per stage | S-02 tile | reject-rate spike | — after EPIC-OBS-1 |
| Lifecycle | transition log | illegal-transition meter, status latency | consumer spans | timeline | illegal>0 | — |
| Segmented budget | — | ingress→validate→route→settle→pacs002→e2e histograms | one continuous trace | SLA board | e2e p95 breach | `[ADD]` Iter 2 (was designed, unowned R-17) |
| Kafka | consumer logs | **lag per group (first-class, done 2026-07-14)**, retry count, DLQ depth | header propagation | lag board | DLQ>0 on `csm.response`, recon topics | retry/DLQ/alert still `[ADD]`, blocked on DLQ mechanism + recon topics |
| Ledger | entry log | drift gauge (snapshot vs derived), reserve latency | — | S-10 | drift≠0 = CRITICAL | — |
| Settlement | cycle log | cycle-close ±30s, insufficiency rate | cycle span | S-08/cycle | close overdue | — |
| Egress | attempt log | pending/claimed/delivered/failed, retry depth, receipt lag | dispatch span | S-11/S-12 | dead-letter>0; status-out >5s | topic drift R-10 |
| Reconciliation | run log | run duration, exceptions by severity, ageing vs SLA | run span | S-14/S-13 | CRITICAL exception immediate; run.failed | — |
| Case (P1) | decision log | open-case ageing, escalations | — | S-15 | expired cases | — |
| Simulation | scenario log | injected-anomaly counts, run duration | scenario trace | S-17 | — | — |
| Security/audit | authz-denied log | denied count | — | — | audit-write failure = page | hash-chain `[P1]` |
| Dead-letter handling | DLQ consumer logs | per-topic DLQ depth | — | lag board | any DLQ growth | operator worklist R-18 (P1) |
| Operator queues | — | queue depths (orphans/exceptions/dead-letters/cases) | — | S-01 (P1) | depth thresholds | `[P1]` |

## Rollout sequencing (per this session's plan)

- **Story 25.1** (this file, Iteration 0/1): the inventory itself.
- **Story 25.2** (Iteration 1-2): "Segmented budget" row — requires ingress→validate→route→settle→pacs002→e2e to all exist as real stages. Blocked today: only `ingress` (EPIC-19 Story 19.1) exists; `validate`/`route`/`settle`/`pacs002` stages belong to epics not yet built (EPIC-20 Story 20.3, EPIC-27, EPIC-32+, EPIC-35+).
- **Story 25.3** (Iteration 3): "Kafka" row — lag-per-consumer-group is now built (`KafkaConsumerGroupLagGauge`, real Micrometer gauge for `payment-lifecycle-inbox`, exposed via `/actuator/metrics/kafka.consumer.lag`). The DLQ-alerting half remains blocked: no DLQ mechanism exists in `InboxConsumer`, and it would target `csm.response`/reconciliation topics that don't exist yet (EPIC-17/EPIC-59+, both not-started).
- **Story 25.4** (Iteration 4): generalized alert rules layered on top of 25.2/25.3 — blocked transitively by both.

## Status

Story 25.1 is **done** (this file exists and is sourced verbatim from §17, not invented). Stories 25.2–25.4 remain `blocked` in `planning/epics/EPIC-25-observability-consolidation.md` for the structural reasons above — none require a user decision, they require the pipeline stages/topics/modules that later epics will build.

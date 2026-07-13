---
status: not-started
depends_on: [EPIC-19-ingress-staging-pipeline]
source: "sepa-nexus-message-flow-and-data-blueprint.md §8 (EPIC-CORE-1, line 1248)"
---

# EPIC-20 — Payment Lifecycle: FSM (EPIC-CORE-1)

## Story 20.1 — Tabela przejść + guardy

status: not-started
depends_on: []

Taski:
- [ ] **Zaimplementuj tabelę przejść FSM `Payment`/`PaymentLifecycle` z guardami zgodnie z modelem statusów w main blueprincie.**
      `verify: ./mvnw -f backend test -Dtest=*PaymentFsmTransitionTest*`

## Story 20.2 — Konsument Kafka + inbox

status: not-started
depends_on: [Story 20.1, EPIC-04-outbox-inbox-kafka-thin]

Opis: testy duplikatów/kolejności.

Taski:
- [ ] **`@KafkaListener` na eventach cyklu życia z dedupem przez `payment.inbox_events`, testy duplikatu i out-of-order.**
      `verify: ./mvnw -f backend test -Dtest=*PaymentLifecycleConsumerTest*`

## Story 20.3 — Korelacja status-inbound (G4)

status: not-started
depends_on: [Story 20.1, EPIC-26-iso-message-lineage-core]

Opis: lookup przez `iso.payment_iso_identifiers`, orphan → DLQ.

Taski:
- [ ] **Korelacja statusu przychodzącego przez `iso.payment_iso_identifiers`; brak dopasowania → DLQ, nie cichy no-op.**
      `verify: ./mvnw -f backend test -Dtest=*StatusInboundCorrelationTest*`

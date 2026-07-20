---
name: debina-kafka-payment-contract
description: Use for Kafka topics, producers/consumers, consumer groups, payment events/envelopes, schema versions, partition keys, ordering, outbox/inbox, dispatcher/relay, offsets, acknowledgements, redelivery, retries, DLQs, schedulers, consumer lag, or payment.sla.breached; do not use for ordinary synchronous calls, generic non-Kafka application events, or pure database work with no event publication.
---
# Kafka payment contract

Preserve source precedence: accepted ADR and `[FREEZE]` → authoritative source → accepted decision → `HANDOFF.md` → capability/readiness → implementation evidence. Begin with ADR-N8 and §3.7 v2; a Java class name, topic string, epic title, or runtime convenience is not a contract.

## Contract workflow

1. Locate the sole source-of-truth topic-catalog row and identify its one producer and contract owner. If the topic, owner, payload, key, or policy is absent, record `SOURCE-BLOCKED`; do not create it.
2. Record every dimension in [topic-contract-checklist.md](references/topic-contract-checklist.md), including source-backed ordering and data classification.
3. Apply ADR-N5 ownership: producer writes a module-owned outbox only; consumers own their inbox/dedup and never write foreign schemas. Name actual module role, datasource, transaction/offset boundary, dispatcher health, and failure visibility.
4. Specify the exact dispatch sequence: database commit makes an outbox row eligible; broker acknowledgement precedes its published marker. Do not create false published state after a failed send.
5. Analyze applicable windows in [delivery-and-offset-failure-matrix.md](references/delivery-and-offset-failure-matrix.md). Design replay-safe behavior; Kafka transactions alone do not establish exactly-once business semantics.
6. Read [ownership-and-versioning-model.md](references/ownership-and-versioning-model.md) for catalog, compatibility, partition, retry, DLQ, and data-boundary rules. Run the real PostgreSQL 18/Kafka evidence in [runtime-proof-matrix.md](references/runtime-proof-matrix.md).

## Non-negotiable boundaries

Do not invent a topic, event, payload, retry count, business timeout, or `payment.sla.breached` contract from an epic title. Do not add a DLQ without an explicit operational contract, silently drop an event, mark published before broker acknowledgement, treat a topic name as its full schema, expose confidential payment data without classification, or share a broad database writer role among consumers.

Do not introduce choreography, saga semantics, settlement finality, or a new business policy without an accepted ADR and source. Event delivery, `ACSC`, delivery, receipt, or a successful consumer does not establish settlement finality. Keep external Kafka contracts separate from in-process Modulith interactions and from planning readiness gates.

## References

- [topic contract checklist](references/topic-contract-checklist.md)
- [delivery and offset failure matrix](references/delivery-and-offset-failure-matrix.md)
- [ownership and versioning model](references/ownership-and-versioning-model.md)
- [runtime proof matrix](references/runtime-proof-matrix.md)

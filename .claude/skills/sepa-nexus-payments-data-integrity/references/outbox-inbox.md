# Outbox / inbox

`EPIC-04` established the thin outbox/inbox pattern for reliable Kafka delivery from a transactional write. The pattern exists specifically so a business write and its downstream event publication are atomic from the writer's perspective (both commit together, or neither does), without depending on Kafka being reachable at write time.

## Never bypass it for a "quick" produce/consume

Any code path that needs at-least-once delivery of a domain event alongside a transactional write goes through the existing outbox table (write the event row in the same transaction as the business write; a separate relay process/poller publishes to Kafka afterward), never a direct `KafkaTemplate.send()` call from inside the same transaction as the business write — a direct send can succeed while the transaction later rolls back (or vice versa), producing an event for a write that never actually committed, or a committed write with no corresponding event.

## Inbox side: idempotent consumption

A Kafka consumer that triggers a business write must be idempotent against redelivery (Kafka's at-least-once guarantee means the same message can be delivered more than once) — use the existing inbox pattern (a processed-message-ID record, checked before applying the business effect) rather than assuming "consumers only see each message once."

## Existing example to follow, not reinvent

`PaymentHistoryRecorder` / `InboxConsumer` (per `planning/epics/EPIC-11-payment-slim-ownership.md` Story 11.1) is the worked example of atomic history-recording via this pattern in this codebase — a new module needing the same guarantee should follow its shape rather than inventing a parallel mechanism.

## What this pattern is not for

Not every cross-module signal needs outbox/inbox — a synchronous, in-process Spring Modulith `@ApplicationModuleListener` within the same transaction/JVM doesn't need Kafka-grade reliability machinery. Reserve outbox/inbox for genuinely asynchronous, cross-process (Kafka) delivery.

# Idempotency

## Explicit key, explicit scope

Every retryable write endpoint takes an `Idempotency-Key` header (or equivalent explicit field) — never infer idempotency from business fields alone (e.g. "same amount + same payee within a minute" is a heuristic, not a guarantee). The uniqueness scope is `(tenant_id, endpoint/operation, idempotency_key)` — a key is only meaningful within its tenant and its specific operation, never global.

## Required behavior matrix

| Scenario | Required behavior |
|---|---|
| Same key, same payload, first time | Process normally, record the key |
| Same key, same payload, replay | Return the *original* result without reprocessing (no duplicate side effect — no second payment created, no second Kafka event published) |
| Same key, **different** payload | Reject with a conflict (409-equivalent) — never silently process the new payload, never silently return the old result for a materially different request |
| Different key, same payload | Two independent operations — this is not what idempotency keys protect against; duplicate-content detection (if wanted) is a separate concern, not idempotency |

## Storage

An idempotency record needs at minimum: the scope tuple, a payload hash (to detect the "same key, different payload" case without storing/comparing the full payload every time, though storing the full result to replay is often also required), and enough of the original result to answer a replay without redoing side-effecting work.

## Concurrency is the real test

The scenario that actually breaks naive idempotency implementations is two concurrent requests with the same key arriving close together — a check-then-insert without a database-level uniqueness constraint racing under load. Required test: fire two (or more) concurrent requests with the same idempotency key against a real PostgreSQL Testcontainers instance (not mocked, not sequential-only), assert exactly one side effect occurred and the other request either replayed the result or waited/retried correctly — never that both produced independent side effects. A unique constraint on the scope tuple, with the insert-conflict path routed to "look up and return the existing result," is the standard mechanism; a test that only calls the endpoint sequentially twice does not exercise this and is not sufficient proof.

## Relationship to ISO identifiers

Per `iso20022-identifiers.md` and `EPIC-21` Story 21.2's resolved decision, `Idempotency-Key` — not `EndToEndId` or any other ISO identifier — is this project's sole exactly-once guarantee for payment submission. Do not add a second, competing uniqueness mechanism keyed on an ISO identifier without first checking whether that decision still applies.

---
name: sepa-nexus-payments-data-integrity
description: Use when touching money/amount handling, ISO 20022 identifiers (MsgId/EndToEndId/TxId/UETR/Orgnl*), idempotency keys, evidence/audit records, ledger entries, or correlation logic (adapter matching pacs.002 to a payment) — the data-integrity rules specific to this payment platform's domain.
---
# Payments data integrity

## Money

- `numeric` only, never `float`/`double` — explicit precision/scale on every money column (e.g. `numeric(18,2)` — confirm the actual scale needed per currency/amount type against the source blueprint, don't default to 2 for currencies with different minor-unit conventions).
- Currency is always explicit and stored alongside the amount, never assumed from context.
- No money arithmetic on floating-point types anywhere in the pipeline (Java `BigDecimal`/`numeric`, never `double`, for any amount calculation).
- Ledger is append-only (see `references/ledger-invariants.md`); a reversal is a new, explicit opposite-direction entry, never a mutation or deletion of a prior one — matches the root `AGENTS.md` frozen rule "a return-after-finality is a new, opposite-direction payment, never a ledger reversal."

## ISO 20022 identifiers

Raw XML and the canonical model are distinct — never let the canonical model silently drop or conflate identifiers. `MsgId`, `EndToEndId`, `TxId`, `UETR`, and the `Orgnl*` family (original-message correlation identifiers on pacs.002/R-messages) each mean something different and must never overwrite one another. Full detail: `references/iso20022-identifiers.md`.

## Idempotency

Every write endpoint that can be retried needs an explicit idempotency key with an explicit scope (tenant + endpoint + key, not just key). Same key + same payload → same result, replayed not reprocessed. Same key + different payload → conflict, never silently accepted. Race condition (two concurrent requests, same key) must be tested against a real PostgreSQL Testcontainers instance, not assumed safe from application-level locking alone. Full detail: `references/idempotency.md`.

## Evidence / audit

`evidence.*`/`audit.*` are append-only, never deduplicated at write time (two identical-looking evidence records from two real events are two real facts, not a duplicate to collapse) and never treated as a business model in their own right — they record what happened, they don't drive FSM decisions. Never log full XML payloads or other potentially sensitive content into evidence/audit records or application logs. Full detail: `references/append-only-evidence.md`.

## Correlation (`iso-adapter` ↔ `payment-lifecycle`)

Binding rule (see root `AGENTS.md`): **adapter correlates, payment-lifecycle runs the FSM.** No business decision is made inside `iso-adapter`. Correlation results are exactly one of `MATCHED` / `AMBIGUOUS` / `ORPHANED` — no best-guessing under ambiguity. Duplicates and out-of-order messages are explicitly modeled outcomes, not errors. Full detail: `references/correlation-integrity.md`.

## Outbox/inbox

Kafka transport reliability is handled by the existing outbox/inbox pattern (`EPIC-04`) — never bypass it with a direct produce/consume call from business logic that needs at-least-once delivery guarantees. Full detail: `references/outbox-inbox.md`.

## References

- `references/money-and-currency.md`
- `references/iso20022-identifiers.md`
- `references/idempotency.md`
- `references/append-only-evidence.md`
- `references/ledger-invariants.md`
- `references/outbox-inbox.md`
- `references/correlation-integrity.md`

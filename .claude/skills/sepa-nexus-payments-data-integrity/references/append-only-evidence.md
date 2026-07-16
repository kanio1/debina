# Evidence and audit records

## Append-only, always

`evidence.*` and `audit.*` tables are write-once — `INSERT` only, `UPDATE`/`DELETE` forbidden at the grant level (the app role should not hold `UPDATE`/`DELETE` privileges on these tables; verify this at the SQL-grant layer, not just by convention in application code). See `sepa-nexus-database-testing` skill's non-vacuous append-only test pattern (attempt an `UPDATE`/`DELETE`, assert `42501`).

## No deduplication at write time

Two evidence records that look identical (same message, same timestamp-adjacent, same actor) from two genuinely separate events are two real facts — recording both is correct, not a bug to "fix" by deduplicating. Evidence's job is to answer "what happened, when, and in what order," not to present a deduplicated summary — that's a read-model/reporting concern layered on top, never a mutation of the evidence itself.

## Evidence is not a business model

Never let application business logic (FSM transitions, correlation decisions, settlement logic) read *from* an evidence/audit table as its source of truth, and never let evidence recording be the mechanism that *drives* a state transition. Evidence records what a business decision already made determined; it doesn't make the decision. If code needs "did X happen," that's answered by the owning module's actual state (payment status, correlation result, etc.), not by querying the evidence trail as a side-channel truth source.

## Raw archive doesn't deduplicate either

The raw inbound message archive (`ingress.raw_inbound_messages`) keeps every received message as received, even if content-identical to a prior one — a resubmission or genuine duplicate delivery is itself evidence, and collapsing it at the archive layer would destroy the ability to later distinguish "received twice" from "received once." Deduplication (if the business logic needs it, e.g. via idempotency key) happens at the processing layer, never at the archive layer.

## Never log sensitive payloads

Full XML message bodies, full PII fields, and full payment payloads must never be written into application logs (as opposed to the dedicated, access-controlled evidence/audit tables, which have their own PII/retention handling — see `planning/README.md`'s open question on the PII/GDPR boundary, not yet resolved). If a log line needs to reference a specific message/payment, log its identifier (message ID, payment ID), never its content. This applies equally to `SECURITY DEFINER` function bodies and any new logging statement added alongside a migration or evidence-recording change.

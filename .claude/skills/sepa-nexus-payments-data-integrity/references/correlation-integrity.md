# Correlation integrity

Binding rule (root `AGENTS.md`, repeated here because it's the single most load-bearing rule for this concern): **adapter correlates, payment-lifecycle runs the FSM.** `iso-adapter` never makes a business decision — it only determines *what a status/return message refers to*; `payment-lifecycle` decides *what that means for the payment's state*.

## Three, and only three, correlation outcomes

- **`MATCHED`** — the incoming pacs.002/R-message's `Orgnl*` identifiers resolve to exactly one existing payment/message, unambiguously.
- **`AMBIGUOUS`** — the identifiers resolve to more than one plausible candidate. Never resolved by picking "the most likely one," "the most recent one," or any other heuristic inside `iso-adapter` — ambiguity is itself the correct, final output of correlation, to be handled explicitly downstream (see `EPIC-27` Story 27.5, `[P1]` manual-correlation read model).
- **`ORPHANED`** — the identifiers resolve to no known payment/message. Routed to DLQ (`EPIC-27` Story 27.4), not silently dropped and not guessed-and-attached to something unrelated.

No fourth outcome, and no "best guess" fallback folded into `MATCHED` when the actual match confidence doesn't warrant it — a false `MATCHED` is worse than a correctly-reported `AMBIGUOUS`, because it would drive an FSM transition on the wrong payment.

## Duplicate and out-of-order are explicitly modeled, not errors

- **Duplicate** correlation input (the same pacs.002 message, or a message that would resolve to a correlation already recorded) → an explicit `IGNORED_DUPLICATE` outcome (see `EPIC-27` Story 27.3), not a thrown exception and not a silent no-op that leaves no trace.
- **Out-of-order** delivery (a status message arriving before the system has otherwise "expects" it, e.g. before the FSM state it would normally follow) → handled by `payment-lifecycle`'s own FSM policy for out-of-order transitions, never treated as a correlation-layer error. `iso-adapter` correlates the message to its payment regardless of arrival order; whether that's a valid transition *right now* is entirely `payment-lifecycle`'s call.

## Extraction vs. correlation are separate steps

Pulling `OrgnlMsgId`/`OrgnlEndToEndId` (and the rest of the `Orgnl*` family) out of a parsed pacs.002 is pure extraction — a read-only, deterministic parse operation with no matching/decision logic (`EPIC-27` Story 27.1). Deciding what those extracted identifiers *correlate to* — querying existing payments/messages, applying the 9-step correlation policy, producing MATCHED/AMBIGUOUS/ORPHANED — is a distinct, later step (`EPIC-27` Story 27.2) that depends on extraction but is not part of it. Do not fold correlation logic into an extraction-scoped story or class — see this repo's own `[SPLIT]` precedent for `EPIC-27` Story 27.4/27.5, which separated an MVP deliverable from a P1 one along exactly this kind of scope boundary.

## No decisions inside `iso-adapter`

`iso-adapter` never writes to `payment.*`, never transitions FSM state, and never decides finality. Its output (the correlation result) is consumed by `payment-lifecycle`, which owns every subsequent decision. A code review finding "correlation logic reaching into payment-lifecycle's tables or FSM" is a binding-rule violation, not a style nit — flag it as such.

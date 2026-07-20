---
name: debina-payment-state-finality
description: Use for payment FSM, business/ISO/finality/transport/receipt status, ACSC, RJCT, DISPATCHED, DELIVERED, return, recall, timeout, revocation cutoff, or finality evidence; keep five status axes separate.
---
# Payment state and finality

Keep five separate axes: business lifecycle, ISO/message status, settlement finality, transport/delivery status, and receipt/reconciliation status. FSM terminality, `REJECTED`, `DISPATCHED`, `POSTED` (without approved policy), `ACSC`, `DELIVERED`, transport confirmation and receipt are not finality. Transport failure never unsets finality; late receipt never overwrites it. `payment_status_history.is_final` is projection compatibility data, and business transitions must not derive it. A return after finality is a new payment, never reversal.

Finality requires a settlement-owned record, immutable profile snapshot, versioned finality rule, authoritative source ID/time, evidence hash, one record per payment, replay idempotency, conflicting-evidence fail-closed handling and idempotent payment projection. Execute only rules with real triggers; catalog-only rules receive no invented trigger.

For every status change ask which axis changes, owner, authorizing source event, reversibility, money/finality effect, whether it is transport evidence/projection, duplicate/late/conflict behavior, and authoritative timestamp. Read [five-axis model](references/five-axis-status-model.md), [authority checklist](references/finality-authority-checklist.md), and [return boundaries](references/return-recall-reversal-boundaries.md).

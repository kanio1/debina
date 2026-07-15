-- owner: payment-lifecycle [MVP] — EPIC-11 Story 11.1, backfill phase. Every payment created
-- through the application from this point forward gets a payment_status_history row automatically
-- (PaymentCreationWriter/InboxConsumer). Existing payments predate this table entirely — this
-- migration gives each exactly one MIGRATION_BASELINE row: "the state observed when this table was
-- introduced," never a fabricated reconstruction of when RECEIVED/VALIDATED/etc. actually happened
-- historically (those times were never recorded). from_status is NULL (no prior state is asserted),
-- to_status is the payment's current status, at is the migration's own observation time — not
-- payment.payments.created_at, which would falsely claim "reached this status at creation time" for
-- payments that transitioned later. is_final mirrors PaymentTransitionTable's terminal states
-- (REJECTED/DISPATCHED have zero legal outgoing transitions).

INSERT INTO payment.payment_status_history
    (payment_id, seq, from_status, to_status, status_code, source_type, actor_type, is_final, event_type, at)
SELECT
    id,
    1,
    NULL,
    status,
    status,
    'INTERNAL',
    'SYSTEM',
    status IN ('REJECTED', 'DISPATCHED'),
    'MIGRATION_BASELINE',
    now()
FROM payment.payments;

-- owner: payment-lifecycle — EPIC-33 Story 33.4, blueprint §4.3
--
-- Technical timeout and revocation cutoff are timing facts.  They are deliberately independent
-- from the business FSM and settlement finality; this additive migration creates neither a new
-- status nor a finality trigger.
--
-- Migration impact: nullable metadata-only columns on payment.payments; brief ACCESS EXCLUSIVE
-- lock, no rewrite/backfill; existing RLS and sepa_app writer grants continue to apply.

ALTER TABLE payment.payments
    ADD COLUMN timeout_at timestamptz(3),
    ADD COLUMN revocation_cutoff timestamptz(3);

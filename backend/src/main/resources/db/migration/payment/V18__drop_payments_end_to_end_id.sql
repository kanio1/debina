-- owner: payment-lifecycle + iso-adapter [MVP] — EPIC-21 Story 21.2, contract phase.
-- sepa-nexus-message-flow-and-data-blueprint.md §4.3 (v2 patch): "the old pay_corr unique index on
-- (tenant_id, end_to_end_id) is REMOVED — correlation now lives entirely in
-- iso.payment_iso_identifiers (below); a payment can be found by any ISO id it ever carried." No
-- replacement uniqueness is added: EndToEndId is an ISO lineage/correlation identifier, not a
-- payment business key. Exactly-once submission remains Idempotency-Key's job
-- (ingress.idempotency_keys), never EndToEndId uniqueness — see the decision note in
-- planning/epics/EPIC-21-iso-identifier-refactor.md. V17 backfilled every pre-lineage payment row
-- before this drop, so no identifier is lost.

DROP INDEX payment.payments_tenant_e2e_idx;

ALTER TABLE payment.payments DROP COLUMN end_to_end_id;

CREATE INDEX pii_e2e ON iso.payment_iso_identifiers (end_to_end_id);

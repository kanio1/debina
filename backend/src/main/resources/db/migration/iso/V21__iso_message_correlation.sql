-- owner: iso-adapter [MVP] — sepa-nexus-message-flow-and-data-blueprint.md §4.3c (iso.iso_message_correlation
-- DDL) + §4.4 (per-schema outbox pattern, which explicitly names iso.outbox_events in its own
-- example list) + §4.3c's own iso.iso_messages.tenant_id (deferred from V11's "minimal slice").
-- EPIC-27 Story 27.2C. Three additive, expand-only pieces:
--
--  1. iso.iso_messages.tenant_id — completes source DDL (§4.3c specifies `tenant_id uuid NOT
--     NULL`), backfilled from ingress.raw_inbound_messages via the existing raw_message_id FK.
--     Enables the Story 27.2B correlation policy's candidate lookup to stay tenant-safe entirely
--     inside the iso schema, without iso-adapter ever reading payment.* directly (root AGENTS.md/
--     §3.6.3 binding rule). JsonDirectLineageRecorder/Pain001LineageRecorder now both pass
--     tenantId on every INSERT (this migration's companion code change) — but V17's earlier
--     backfill of pre-history walking-skeleton smoke rows never set raw_message_id at all, so a
--     handful of legacy rows cannot be backfilled here and stay NULL. NOT NULL is therefore
--     deliberately NOT enforced at the DDL level (unlike source's own NOT NULL) — those rows
--     simply never match any tenant-scoped correlation query, which is safe (they predate any
--     real payment history), rather than fabricating a tenant_id or reaching into
--     payment.payments from an iso-schema migration to backfill them.
--  2. iso.payment_iso_identifiers.tx_id — completes source DDL §4.3c; the candidate-side column
--     for correlation strategy 1 (OrgnlMsgId+OrgnlTxId). No channel populates it yet (pacs.008
--     doesn't exist in this system) — nullable, always NULL today. The column exists so strategy
--     1 is structurally correct and forward-compatible, not to invent data.
--  3. iso.iso_message_correlation (new table, source DDL verbatim) + iso.outbox_events (new
--     table, same shape/grant pattern as the existing payment.outbox_events — this schema's
--     ALTER DEFAULT PRIVILEGES from V11 already covers it, no extra GRANT needed).

ALTER TABLE iso.iso_messages
    ADD COLUMN tenant_id uuid;

UPDATE iso.iso_messages im
SET tenant_id = rim.tenant_id
FROM ingress.raw_inbound_messages rim
WHERE im.raw_message_id = rim.id
  AND im.tenant_id IS NULL;

CREATE INDEX iso_msg_tenant_idx ON iso.iso_messages (tenant_id);

ALTER TABLE iso.payment_iso_identifiers
    ADD COLUMN tx_id text;

CREATE TABLE iso.iso_message_correlation (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    iso_message_id uuid NOT NULL REFERENCES iso.iso_messages (id),
    correlation_type text NOT NULL CHECK (correlation_type IN
        ('PACS002_TO_PAYMENT', 'PACS004_TO_PAYMENT', 'CAMT056_TO_PAYMENT', 'CAMT029_TO_CASE', 'CAMT053_TO_STATEMENT')),
    matched_payment_id uuid,
    matched_case_id uuid,
    matched_outbound_message_id uuid,
    status text NOT NULL CHECK (status IN ('MATCHED', 'ORPHANED', 'AMBIGUOUS', 'IGNORED_LATE', 'IGNORED_DUPLICATE')),
    score smallint CHECK (score BETWEEN 0 AND 100),
    ambiguity_reason text,
    matched_by text,
    created_at timestamptz(3) NOT NULL
);

CREATE INDEX imc_status ON iso.iso_message_correlation (status, created_at DESC);
CREATE INDEX imc_payment ON iso.iso_message_correlation (matched_payment_id);

-- Append-only: a correlation decision is a fact recorded once (Story 27.3's IGNORED_DUPLICATE/
-- IGNORED_LATE outcomes are new rows for new correlation attempts, never an UPDATE of this row).
REVOKE UPDATE, DELETE ON iso.iso_message_correlation FROM sepa_app;

CREATE TABLE iso.outbox_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id uuid NOT NULL,
    event_type text NOT NULL,
    payload jsonb NOT NULL,
    correlation_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz
);

CREATE INDEX iso_outbox_unpublished_idx
    ON iso.outbox_events (created_at)
    WHERE published_at IS NULL;

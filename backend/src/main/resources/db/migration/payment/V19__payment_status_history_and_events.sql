-- owner: payment-lifecycle [MVP] — sepa-nexus-message-flow-and-data-blueprint.md §4.3 (lines
-- 538-562), OQ-12 resolution (EPIC-11 Story 11.1). Ownership confirmed by
-- sepa-nexus-blueprint-ownership-integration.md §3.6.2 (line 132: payment_status_history/
-- payment_events owned by payment-lifecycle, writer "payment-lifecycle only") and its read-model
-- table (line 289: payment_timeline_view sources payment_status_history + payment_events +
-- iso.payment_iso_identifiers).
--
-- Deviations from the full §4.3 DDL sketch, both intentional and documented:
-- (1) payment_events is NOT partitioned here (source: "PARTITION BY RANGE (at)") — the same
--     simplification V10 already made for ingress.raw_inbound_messages ("no monthly partitioning
--     yet ... partitioning is a later DBA-hardening pass, not required to prove the ... contract").
--     No source document resolves an operational partition-lifecycle strategy (who pre-creates next
--     month's partition, what happens on the boundary) — payment_events partition lifecycle remains
--     an open question, deliberately not solved here rather than shipping a table that stops
--     accepting writes at a month boundary.
-- (2) Neither table gets a tenant_id/branch_id column or its own RLS policy — the source DDL sketch
--     has none on either table. Tenant/branch scoping is achieved by always resolving payment_id
--     through the RLS-protected payment.payments read path first (every read in this module already
--     does this), never by a standalone query against these tables — the "enterprise-ready variant"
--     of ownership-protected base history + tenant-scoped query through the payment read model.
-- (3) status_code/reason_code are plain text, not real FK constraints — the source DDL itself marks
--     them "FK-able to reference_data.status_catalog/iso_reason_codes" (a comment, not a
--     REFERENCES), and those catalogs do not exist yet (EPIC-11 Story 11.2, blocked). Values
--     populated here use the existing 4-value PaymentStatus enum (RECEIVED/VALIDATED/REJECTED/
--     DISPATCHED) — the richer business_status_code taxonomy from §4.3's payments table is a
--     separate, larger EPIC-11 concern, not this migration's.
--
-- Append-only like signature/iso_message_parse_errors evidence: sepa_app may INSERT/SELECT, never
-- UPDATE/DELETE (grants inherited from V2's ALTER DEFAULT PRIVILEGES on schema payment).
--
-- payment_id deliberately carries no REFERENCES payment.payments(id) — matching the established
-- convention already set by iso.payment_iso_identifiers/iso.message_lineage (V11), which have the
-- same plain "payment_id uuid NOT NULL" with no FK. The reason is concrete, not stylistic: the
-- runtime writer (PaymentCreationWriter) creates the Payment via JPA (paymentRepository.save,
-- UUID-generated, no forced flush) and then writes history/event rows via a raw JdbcTemplate insert
-- in the same transaction — a real FK here fails with a foreign-key-violation because the JPA
-- INSERT has not necessarily hit the database yet when the JDBC INSERT runs. Referential integrity
-- to payment.payments is enforced at the application layer (PaymentCreationWriter/InboxConsumer are
-- the only writers, both always given a payment_id that was just persisted in the same transaction).

CREATE TABLE payment.payment_status_history (
    payment_id uuid NOT NULL,
    seq int NOT NULL,
    from_status text,
    to_status text NOT NULL,
    status_code text NOT NULL,
    reason_code text,
    source_type text NOT NULL,
    source_iso_message_id uuid REFERENCES iso.iso_messages (id),
    raw_message_id uuid REFERENCES ingress.raw_inbound_messages (id),
    actor_type text NOT NULL DEFAULT 'SYSTEM',
    is_final boolean NOT NULL DEFAULT false,
    event_type text NOT NULL,
    event_ref uuid,
    at timestamptz(3) NOT NULL,
    PRIMARY KEY (payment_id, seq)
);

CREATE TABLE payment.payment_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id uuid NOT NULL,
    type text NOT NULL,
    payload jsonb NOT NULL,
    at timestamptz(3) NOT NULL
);

CREATE INDEX payment_events_payment_idx ON payment.payment_events (payment_id, at);
CREATE INDEX payment_events_brin_idx ON payment.payment_events USING brin (at);
CREATE INDEX payment_events_payload_gin_idx ON payment.payment_events USING gin (payload jsonb_path_ops);

REVOKE UPDATE, DELETE ON payment.payment_status_history FROM sepa_app;
REVOKE UPDATE, DELETE ON payment.payment_events FROM sepa_app;

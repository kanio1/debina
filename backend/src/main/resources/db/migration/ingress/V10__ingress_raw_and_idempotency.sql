-- owner: ingress [MVP] — sepa-nexus-message-flow-and-data-blueprint.md §4.2 DDL sketch
-- Simplified from the full sketch for this pass: no monthly partitioning yet (correctness first,
-- partitioning is a later DBA-hardening pass, not required to prove the idempotency contract).
-- ingress/iso-adapter are not yet split into their own Spring Modulith modules (only
-- payment-lifecycle exists) — sepa_app remains the sole writer of both new schemas below, same
-- as it already is for `payment`, so one-writer-per-schema still holds.

CREATE SCHEMA ingress AUTHORIZATION sepa_migration;

GRANT USAGE ON SCHEMA ingress TO sepa_app;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA ingress TO sepa_app;
ALTER DEFAULT PRIVILEGES FOR ROLE sepa_migration IN SCHEMA ingress
    GRANT SELECT, INSERT, UPDATE ON TABLES TO sepa_app;

CREATE TABLE ingress.raw_inbound_messages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    received_at timestamptz(3) NOT NULL DEFAULT now(),
    channel text NOT NULL,
    tenant_id uuid NOT NULL,
    message_type text,
    payload bytea NOT NULL,
    payload_sha256 bytea NOT NULL
    -- [CHANGE, per source] no unique constraint on payload_sha256: this is append-only evidence,
    -- not a dedupe gate — a legitimate resend must still archive a second row.
);

CREATE INDEX rim_tenant_received_idx ON ingress.raw_inbound_messages (tenant_id, received_at DESC);

CREATE TABLE ingress.idempotency_keys (
    source_id uuid NOT NULL,
    idem_key text NOT NULL,
    request_hash bytea NOT NULL,
    payment_id uuid,
    raw_message_id uuid REFERENCES ingress.raw_inbound_messages (id),
    response_code smallint,
    first_seen_at timestamptz(3) NOT NULL DEFAULT now(),
    last_seen_at timestamptz(3) NOT NULL DEFAULT now(),
    PRIMARY KEY (source_id, idem_key)
);

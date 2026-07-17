-- owner: egress [MVP] — sepa-nexus-message-flow-and-data-blueprint.md §6.2/§6.4, EPIC-43 Story 43.1
--
-- Scope decision: this migration builds only the columns Story 43.1's own claim mechanism needs
-- to exist meaningfully (identity + payload + state + created_at, all NOT NULL in §6.4's DDL
-- sketch) — payment_id/cycle_id/file_id (correlation, needed once artifacts exist), signature
-- (Story 43.2 — SignatureSigningPort integration), attempts/next_retry_at (Story 43.3 —
-- retry/backoff), batch_group (Story 43.4 — batch collector) and delivered_at (Story 43.4/43.5 —
-- delivery/receipt) are deliberately deferred to the migrations those later stories add, not
-- invented here ahead of the capability that needs them.
--
-- state vocabulary: §6.4's DDL comment ("PENDING|DELIVERED|CONFIRMED|FAILED|ABANDONED") is an
-- older, simplified sketch that predates §6.2's fuller named lifecycle
-- (REQUESTED→RENDERED→SIGNED→CLAIMED_FOR_DELIVERY→DELIVERED→RECEIPT_RECEIVED→CLOSED, failure
-- branch DELIVERY_FAILED→RETRY_SCHEDULED→DELIVERED|DEAD_LETTERED|MANUAL_INTERVENTION) — the two
-- are the same table described at two different points in the document's history. §6.2's is used
-- here: it is the more detailed, more recently-elaborated description, and "CLAIMED_FOR_DELIVERY"
-- textually matches this story's own claim/dispatch terminology exactly. All 11 canonical values
-- are declared now (matching this repository's own precedent of declaring a full known status
-- vocabulary before every transition is implemented, e.g. signature.signature_keys.status) even
-- though only REQUESTED and CLAIMED_FOR_DELIVERY are reachable until Stories 43.2-43.5 land.

CREATE TABLE egress.outbound_messages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    branch_id uuid,
    recipient_id uuid NOT NULL,
    channel text NOT NULL,
    artifact_type text NOT NULL,
    correlation_msg_id text NOT NULL,
    payload bytea NOT NULL,
    payload_sha256 bytea NOT NULL,
    state text NOT NULL DEFAULT 'REQUESTED' CHECK (state IN (
        'REQUESTED', 'RENDERED', 'SIGNED', 'CLAIMED_FOR_DELIVERY', 'DELIVERED',
        'RECEIPT_RECEIVED', 'CLOSED', 'DELIVERY_FAILED', 'RETRY_SCHEDULED',
        'DEAD_LETTERED', 'MANUAL_INTERVENTION')),
    created_at timestamptz(3) NOT NULL DEFAULT now()
);

CREATE INDEX outbound_messages_claim_idx
    ON egress.outbound_messages (created_at)
    WHERE state = 'REQUESTED';

-- Two-level RLS (§4.7 "Tenant-facing operational — Adopt (two-level)", outbound_messages listed
-- explicitly), mirroring payment.payments' V12 pattern exactly, plus a narrow system-relay policy
-- (§4.7's own p_system_relay example) so the dispatcher can claim across every tenant in one pass —
-- a background system job, not a tenant-scoped request.
ALTER TABLE egress.outbound_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE egress.outbound_messages FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON egress.outbound_messages
    USING (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
        AND (
            NULLIF(current_setting('app.branch_id', true), '') IS NULL
            OR branch_id = NULLIF(current_setting('app.branch_id', true), '')::uuid
        )
    );

CREATE POLICY dispatcher_claim ON egress.outbound_messages
    USING (current_setting('app.role', true) = 'system_relay')
    WITH CHECK (current_setting('app.role', true) = 'system_relay');

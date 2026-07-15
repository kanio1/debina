-- owner: signature [MVP] — sepa-nexus-signature-module-blueprint.md §3/§5 (EPIC-31 Story 31.1)
-- Schema + dedicated writer role + the three tables sketched in §5. No verification/signing logic
-- yet (Story 31.2/31.3) — this migration only establishes the ownership boundary so later work
-- cannot violate it. Unlike ingress/iso (still nested inside payment-lifecycle, sole-writer
-- sepa_app per V10's note), signature is a genuine, separate module from day one: sepa_app gets
-- NO grant here at all — the blueprint's "no other module reads/writes signature.* directly"
-- rule (§3) is enforced at the SQL layer, not just by convention. The module's own runtime code
-- talks to this schema through a dedicated signature_role connection, never the shared sepa_app pool.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'signature_role') THEN
        CREATE ROLE signature_role LOGIN PASSWORD 'dev-only-signature';
    END IF;
END
$$;

CREATE SCHEMA signature AUTHORIZATION sepa_migration;

GRANT USAGE ON SCHEMA signature TO signature_role;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA signature TO signature_role;
ALTER DEFAULT PRIVILEGES FOR ROLE sepa_migration IN SCHEMA signature
    GRANT SELECT, INSERT, UPDATE ON TABLES TO signature_role;

-- Synthetic key registry (§8): a key is (participant | platform, purpose, algo, validity window,
-- status). participant_id has no FK today — reference_data.participants does not exist yet
-- (not part of any migration so far); this column stays a plain nullable uuid until that catalog
-- is built.
CREATE TABLE signature.signature_keys (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_id uuid,
    purpose text NOT NULL CHECK (purpose IN ('VERIFY', 'SIGN', 'BOTH')),
    algo text NOT NULL,
    public_material text NOT NULL,
    private_material_ref text,          -- synthetic lab-only reference, never a real HSM handle
    valid_from timestamptz(3) NOT NULL,
    valid_to timestamptz(3),
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED'))
);

CREATE INDEX signature_keys_lookup_idx
    ON signature.signature_keys (participant_id, purpose, status, valid_from);

-- One row per signature verified or produced (§5). raw_message_id references the ingress raw
-- archive so a verdict always binds back to the exact archived bytes it covers (covered_sha256).
CREATE TABLE signature.message_signatures (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    raw_message_id uuid REFERENCES ingress.raw_inbound_messages (id),
    outbound_artifact_id uuid,
    direction text NOT NULL CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    algo text NOT NULL,
    key_id uuid REFERENCES signature.signature_keys (id),
    signature_bytes bytea NOT NULL,
    covered_sha256 bytea NOT NULL,
    created_at timestamptz(3) NOT NULL DEFAULT now()
);

-- Append-only verdict log: every verify attempt and its outcome (§5/§9), independent of whether a
-- message_signatures row was produced (a FAILED verdict never gets one).
CREATE TABLE signature.signature_verification_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    raw_message_id uuid REFERENCES ingress.raw_inbound_messages (id),
    verdict text NOT NULL CHECK (verdict IN ('VERIFIED', 'FAILED', 'NOT_APPLICABLE')),
    reason_code text,
    key_id uuid REFERENCES signature.signature_keys (id),
    channel text NOT NULL,
    verified_at timestamptz(3) NOT NULL DEFAULT now()
);

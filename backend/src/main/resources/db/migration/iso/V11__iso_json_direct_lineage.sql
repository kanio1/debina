-- owner: iso-adapter [MVP] — sepa-nexus-message-flow-and-data-blueprint.md §4.3b/§4.3c/§2.2a (ADR-N7)
-- Minimal slice needed for JSON_DIRECT lineage (EPIC-19 Story 19.1): iso_message_versions,
-- iso_messages, payment_iso_identifiers, message_lineage. The full 7-9 table §4.3c ISO schema
-- (validation results, correlation, replay log) is out of scope here — it lands with EPIC-10/
-- EPIC-21/EPIC-ISO-* once real XML channels (Story 19.2/19.4, blocked on the signature module)
-- are built. iso-adapter is not yet a separate Modulith module — sepa_app remains the sole writer,
-- same reasoning as V10.

CREATE SCHEMA iso AUTHORIZATION sepa_migration;

GRANT USAGE ON SCHEMA iso TO sepa_app;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA iso TO sepa_app;
ALTER DEFAULT PRIVILEGES FOR ROLE sepa_migration IN SCHEMA iso
    GRANT SELECT, INSERT, UPDATE ON TABLES TO sepa_app;

CREATE TABLE iso.iso_message_versions (
    message_type text NOT NULL,
    effective_from date NOT NULL DEFAULT DATE '2000-01-01',
    validation_profile_code text,
    mapping_profile_code text,
    PRIMARY KEY (message_type, effective_from)
);

-- [SYNTHETIC][ADR-N7] seeded JSON_DIRECT pseudo message-version — §2.2a.
INSERT INTO iso.iso_message_versions (message_type, validation_profile_code, mapping_profile_code)
VALUES ('JSON_DIRECT', 'JSON_DIRECT_PASSTHROUGH', 'JSON_DIRECT_PASSTHROUGH');

CREATE TABLE iso.iso_messages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    direction text NOT NULL CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    message_type text NOT NULL,
    parse_status text NOT NULL,
    raw_message_id uuid REFERENCES ingress.raw_inbound_messages (id),
    created_at timestamptz(3) NOT NULL DEFAULT now()
);

CREATE TABLE iso.payment_iso_identifiers (
    payment_id uuid NOT NULL,
    source_message_type text NOT NULL,
    iso_message_id uuid NOT NULL REFERENCES iso.iso_messages (id),
    end_to_end_id text,
    PRIMARY KEY (payment_id, source_message_type, iso_message_id)
);

CREATE TABLE iso.message_lineage (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    lineage_role text NOT NULL,
    iso_message_id uuid NOT NULL REFERENCES iso.iso_messages (id),
    raw_message_id uuid REFERENCES ingress.raw_inbound_messages (id),
    payment_id uuid,
    created_at timestamptz(3) NOT NULL DEFAULT now()
);

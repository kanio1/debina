-- owner: settlement — ADR-N10, EPIC-39 Story 39.2
--
-- Migration impact
-- Schemat/właściciel: settlement / settlement_role
-- Tabele: new settlement.settlement_profile_snapshots, settlement.settlement_finality_records
-- Obecna najwyższa migracja: V31
-- Czy migracja była już stosowana: no
-- Przewidywany lock: CREATE SCHEMA/TABLE/INDEX/TRIGGER only; no existing-table rewrite.
-- Ryzyko wielkości tabeli: new append-only authority tables.
-- Backward compatibility: additive; V30 remains the history compatibility correction.
-- Expand/contract: expand only.
-- RLS/grants impact: settlement_role writes only its own schema and receives SELECT-only access to
--   the finality-rule catalog; no payment or ledger grant is added.
-- Forward-fix: append-only migration.
-- Fresh DB verification: FinalitySchemaMigrationTest.
-- Upgrade verification: FinalityMigrationUpgradePathTest.

CREATE SCHEMA settlement AUTHORIZATION sepa_migration;

GRANT USAGE ON SCHEMA settlement TO settlement_role;
GRANT USAGE ON SCHEMA reference_data TO settlement_role;
GRANT SELECT ON reference_data.finality_rules TO settlement_role;

CREATE TABLE settlement.settlement_profile_snapshots (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_attempt_id uuid NOT NULL UNIQUE,
    finality_rule_code text NOT NULL,
    finality_rule_version integer NOT NULL,
    created_at timestamptz(3) NOT NULL DEFAULT now(),
    UNIQUE (id, settlement_attempt_id, finality_rule_code, finality_rule_version),
    CONSTRAINT settlement_profile_snapshot_rule_fk
        FOREIGN KEY (finality_rule_code, finality_rule_version)
        REFERENCES reference_data.finality_rules (finality_rule_code, finality_rule_version)
);

CREATE TABLE settlement.settlement_finality_records (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id uuid NOT NULL UNIQUE,
    settlement_attempt_id uuid NOT NULL,
    profile_snapshot_id uuid NOT NULL,
    finality_rule_code text NOT NULL,
    finality_rule_version integer NOT NULL,
    source_type text NOT NULL,
    source_id uuid NOT NULL,
    source_occurred_at timestamptz(3) NOT NULL,
    finality_at timestamptz(3) NOT NULL,
    evidence_hash bytea NOT NULL CHECK (octet_length(evidence_hash) > 0),
    recorded_at timestamptz(3) NOT NULL DEFAULT now(),
    CONSTRAINT settlement_finality_record_snapshot_rule_fk
        FOREIGN KEY (profile_snapshot_id, settlement_attempt_id, finality_rule_code, finality_rule_version)
        REFERENCES settlement.settlement_profile_snapshots
            (id, settlement_attempt_id, finality_rule_code, finality_rule_version),
    CONSTRAINT settlement_finality_record_authoritative_time_check
        CHECK (finality_at = source_occurred_at),
    CONSTRAINT settlement_finality_record_source_key
        UNIQUE (source_type, source_id)
);

CREATE INDEX settlement_finality_records_snapshot_idx
    ON settlement.settlement_finality_records (profile_snapshot_id);

CREATE OR REPLACE FUNCTION settlement.reject_finality_authority_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER settlement_profile_snapshots_immutable
    BEFORE UPDATE OR DELETE ON settlement.settlement_profile_snapshots
    FOR EACH ROW EXECUTE FUNCTION settlement.reject_finality_authority_mutation();

CREATE TRIGGER settlement_finality_records_immutable
    BEFORE UPDATE OR DELETE ON settlement.settlement_finality_records
    FOR EACH ROW EXECUTE FUNCTION settlement.reject_finality_authority_mutation();

REVOKE ALL ON settlement.settlement_profile_snapshots, settlement.settlement_finality_records FROM PUBLIC;
GRANT SELECT, INSERT ON settlement.settlement_profile_snapshots, settlement.settlement_finality_records
    TO settlement_role;

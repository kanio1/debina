-- EPIC-76 Story 76.1 / source: message-flow blueprint §2.2b, §3.6.2, §4.13a.
-- Migration impact
-- - Schema/owner: reference_data / reference-data (reference_data_role runtime writer)
-- - Tables: reference_data.approval_matrix_rules
-- - Current highest migration: V52; append-only forward migration
-- - Lock: CREATE TABLE / indexes only; no rewrite of an existing catalog table
-- - Backward compatibility: additive; payment-lifecycle receives read-only access
-- - RLS/grants: tenant-scoped read/write policy; PUBLIC explicitly revoked
-- - Fresh/upgrade verification: ApprovalPersistenceMigrationTest

CREATE TABLE reference_data.approval_matrix_rules (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL,
    min_amount numeric(18, 2),
    payment_type text,
    max_batch_size int,
    risk_level text,
    requires_approval boolean NOT NULL DEFAULT true,
    requires_step_up boolean NOT NULL DEFAULT false,
    valid_from date NOT NULL,
    valid_to date,
    CHECK (min_amount IS NULL OR min_amount >= 0),
    CHECK (max_batch_size IS NULL OR max_batch_size > 0),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE INDEX approval_matrix_rules_active_tenant_idx
    ON reference_data.approval_matrix_rules (tenant_id, valid_from, valid_to);

ALTER TABLE reference_data.approval_matrix_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE reference_data.approval_matrix_rules FORCE ROW LEVEL SECURITY;

CREATE POLICY approval_matrix_rules_tenant_isolation
    ON reference_data.approval_matrix_rules
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

REVOKE ALL ON TABLE reference_data.approval_matrix_rules FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE ON TABLE reference_data.approval_matrix_rules TO reference_data_role;
GRANT SELECT ON TABLE reference_data.approval_matrix_rules TO sepa_app;

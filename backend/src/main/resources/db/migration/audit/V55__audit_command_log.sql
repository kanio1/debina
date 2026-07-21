-- owner: evidence-audit / source: message-flow §§3.5, 3.6.1-3.6.3, 4.7 and
-- Keycloak security blueprint §§9, 12-13.
--
-- Migration impact
-- - Schema/owner: audit / audit_command_append_owner (NOLOGIN, NOSUPERUSER, NOBYPASSRLS)
-- - Tables: audit.audit_log only; evidence_records and payload_hashes have no executable Wave 8 use case
-- - Current highest migration: V54; append-only forward migration
-- - Lock: catalog/schema/table creation only; no existing-table rewrite or backfill
-- - Backward compatibility: additive; existing Wave 7 payment approvals remain untouched
-- - RLS/grants: FORCE RLS, tenant/branch default-deny reads, narrow auditor read context;
--   sepa_app receives EXECUTE on the typed append function but no table DML
-- - Forward-fix: corrections require a later migration, never an edit to V55
-- - Fresh/upgrade verification: CommandAuditMigrationTest

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_command_append_owner') THEN
        CREATE ROLE audit_command_append_owner NOLOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS;
    END IF;
END
$$;

CREATE SCHEMA audit AUTHORIZATION audit_command_append_owner;
REVOKE ALL ON SCHEMA audit FROM PUBLIC;
GRANT USAGE ON SCHEMA audit TO sepa_app;

CREATE TABLE audit.audit_log (
    audit_entry_id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL,
    branch_id uuid,
    actor_type text NOT NULL CHECK (actor_type IN ('HUMAN', 'SYSTEM')),
    actor_id text NOT NULL CHECK (btrim(actor_id) <> ''),
    authorized_role text NOT NULL CHECK (btrim(authorized_role) <> ''),
    session_id text,
    correlation_id uuid NOT NULL,
    command_type text NOT NULL CHECK (btrim(command_type) <> ''),
    target_type text NOT NULL CHECK (btrim(target_type) <> ''),
    target_id uuid NOT NULL,
    payment_id uuid,
    batch_id uuid,
    decision_comment text,
    before_state jsonb NOT NULL CHECK (jsonb_typeof(before_state) = 'object'),
    after_state jsonb NOT NULL CHECK (jsonb_typeof(after_state) = 'object'),
    outcome text NOT NULL CHECK (outcome IN ('SUCCESS', 'DENIED')),
    command_execution_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL
);

ALTER TABLE audit.audit_log OWNER TO audit_command_append_owner;
ALTER TABLE audit.audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit.audit_log FORCE ROW LEVEL SECURITY;

CREATE POLICY audit_log_tenant_branch_read
    ON audit.audit_log
    FOR SELECT
    USING (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
        AND (NULLIF(current_setting('app.branch_id', true), '') IS NULL
             OR branch_id = NULLIF(current_setting('app.branch_id', true), '')::uuid)
    );

CREATE POLICY audit_log_tenant_branch_append
    ON audit.audit_log
    FOR INSERT
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
        AND (NULLIF(current_setting('app.branch_id', true), '') IS NULL
             OR branch_id = NULLIF(current_setting('app.branch_id', true), '')::uuid)
    );

CREATE POLICY audit_log_auditor_read
    ON audit.audit_log
    FOR SELECT
    USING (current_setting('app.role', true) = 'auditor');

CREATE POLICY audit_log_approval_expiry_append
    ON audit.audit_log
    FOR INSERT
    WITH CHECK (current_setting('app.role', true) = 'system_approval_expiry');

REVOKE ALL ON TABLE audit.audit_log FROM PUBLIC;
REVOKE ALL ON TABLE audit.audit_log FROM sepa_app;

CREATE FUNCTION audit.append_command_audit(
    p_tenant_id uuid,
    p_branch_id uuid,
    p_actor_type text,
    p_actor_id text,
    p_authorized_role text,
    p_session_id text,
    p_correlation_id uuid,
    p_command_type text,
    p_target_type text,
    p_target_id uuid,
    p_payment_id uuid,
    p_batch_id uuid,
    p_decision_comment text,
    p_before_state jsonb,
    p_after_state jsonb,
    p_outcome text,
    p_command_execution_id uuid,
    p_occurred_at timestamptz
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, audit, pg_temp
AS $$
DECLARE
    v_audit_entry_id uuid;
    v_tenant_context text := pg_catalog.nullif(pg_catalog.current_setting('app.tenant_id', true), '');
    v_branch_context text := pg_catalog.nullif(pg_catalog.current_setting('app.branch_id', true), '');
    v_system_context text := pg_catalog.current_setting('app.role', true);
BEGIN
    IF p_tenant_id IS NULL OR v_tenant_context IS NULL OR p_tenant_id::text <> v_tenant_context THEN
        RAISE EXCEPTION 'audit tenant does not match trusted transaction context' USING ERRCODE = '42501';
    END IF;
    IF v_branch_context IS NOT NULL AND p_branch_id IS DISTINCT FROM v_branch_context::uuid THEN
        RAISE EXCEPTION 'audit branch does not match trusted transaction context' USING ERRCODE = '42501';
    END IF;
    IF p_actor_type NOT IN ('HUMAN', 'SYSTEM') OR p_outcome NOT IN ('SUCCESS', 'DENIED')
            OR p_actor_id IS NULL OR btrim(p_actor_id) = ''
            OR p_authorized_role IS NULL OR btrim(p_authorized_role) = ''
            OR p_command_type IS NULL OR btrim(p_command_type) = ''
            OR p_target_type IS NULL OR btrim(p_target_type) = ''
            OR p_target_id IS NULL OR p_correlation_id IS NULL OR p_command_execution_id IS NULL
            OR p_occurred_at IS NULL
            OR jsonb_typeof(p_before_state) <> 'object' OR jsonb_typeof(p_after_state) <> 'object' THEN
        RAISE EXCEPTION 'invalid command audit record' USING ERRCODE = '22023';
    END IF;
    IF p_actor_type = 'SYSTEM' AND v_system_context IS DISTINCT FROM 'system_approval_expiry' THEN
        RAISE EXCEPTION 'system audit actor requires the approved system context' USING ERRCODE = '42501';
    END IF;

    INSERT INTO audit.audit_log (
        tenant_id, branch_id, actor_type, actor_id, authorized_role, session_id, correlation_id,
        command_type, target_type, target_id, payment_id, batch_id, decision_comment,
        before_state, after_state, outcome, command_execution_id, occurred_at)
    VALUES (
        p_tenant_id, p_branch_id, p_actor_type, p_actor_id, p_authorized_role, p_session_id, p_correlation_id,
        p_command_type, p_target_type, p_target_id, p_payment_id, p_batch_id, p_decision_comment,
        p_before_state, p_after_state, p_outcome, p_command_execution_id, p_occurred_at)
    RETURNING audit_entry_id INTO v_audit_entry_id;

    RETURN v_audit_entry_id;
END;
$$;

ALTER FUNCTION audit.append_command_audit(
    uuid, uuid, text, text, text, text, uuid, text, text, uuid, uuid, uuid, text,
    jsonb, jsonb, text, uuid, timestamptz) OWNER TO audit_command_append_owner;
REVOKE ALL ON FUNCTION audit.append_command_audit(
    uuid, uuid, text, text, text, text, uuid, text, text, uuid, uuid, uuid, text,
    jsonb, jsonb, text, uuid, timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION audit.append_command_audit(
    uuid, uuid, text, text, text, text, uuid, text, text, uuid, uuid, uuid, text,
    jsonb, jsonb, text, uuid, timestamptz) TO sepa_app;

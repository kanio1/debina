-- owner: evidence-audit / forward hardening for V55 command audit boundary.
-- Audit readers and the future expiry worker are separate no-inheritance roles; a normal
-- sepa_app connection cannot activate their policies merely by setting app.role.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_auditor_role') THEN
        CREATE ROLE audit_auditor_role LOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS
            PASSWORD 'dev-only-audit-auditor';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'approval_expiry_role') THEN
        CREATE ROLE approval_expiry_role LOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS
            PASSWORD 'dev-only-approval-expiry';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA audit TO audit_auditor_role, approval_expiry_role;
GRANT SELECT ON TABLE audit.audit_log TO sepa_app, audit_auditor_role;
GRANT EXECUTE ON FUNCTION audit.append_command_audit(
    uuid, uuid, text, text, text, text, uuid, text, text, uuid, uuid, uuid, text,
    jsonb, jsonb, text, uuid, timestamptz) TO approval_expiry_role;

DROP POLICY audit_log_auditor_read ON audit.audit_log;
CREATE POLICY audit_log_auditor_read
    ON audit.audit_log
    FOR SELECT
    USING (
        current_user = 'audit_auditor_role'
        AND current_setting('app.role', true) = 'auditor'
    );

DROP POLICY audit_log_approval_expiry_append ON audit.audit_log;
CREATE POLICY audit_log_approval_expiry_append
    ON audit.audit_log
    FOR INSERT
    WITH CHECK (
        session_user = 'approval_expiry_role'
        AND current_setting('app.role', true) = 'system_approval_expiry'
    );

CREATE OR REPLACE FUNCTION audit.append_command_audit(
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
    IF p_actor_type = 'SYSTEM'
            AND (v_system_context IS DISTINCT FROM 'system_approval_expiry'
                 OR session_user <> 'approval_expiry_role') THEN
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
    jsonb, jsonb, text, uuid, timestamptz) TO sepa_app, approval_expiry_role;

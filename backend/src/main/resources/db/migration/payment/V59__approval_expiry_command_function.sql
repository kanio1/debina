-- EPIC-76 Story 76.4 / ADR-W8-01: the scheduler login is not a payment writer.  This narrow
-- payment-owned command changes only due pending approvals and calls the audit append in the
-- same PostgreSQL transaction.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'payment_approval_expiry_function_owner') THEN
        CREATE ROLE payment_approval_expiry_function_owner NOLOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA payment TO payment_approval_expiry_function_owner, approval_expiry_role;
GRANT SELECT, UPDATE (status) ON payment.payment_approvals TO payment_approval_expiry_function_owner;
GRANT SELECT ON payment.payments TO payment_approval_expiry_function_owner;
GRANT EXECUTE ON FUNCTION audit.append_command_audit(
    uuid, uuid, text, text, text, text, uuid, text, text, uuid, uuid, uuid, text,
    jsonb, jsonb, text, uuid, timestamptz) TO payment_approval_expiry_function_owner;

CREATE POLICY payment_approvals_expiry_function_owner
    ON payment.payment_approvals
    FOR ALL
    TO payment_approval_expiry_function_owner
    USING (true)
    WITH CHECK (true);

CREATE POLICY payments_expiry_function_owner
    ON payment.payments
    FOR SELECT
    TO payment_approval_expiry_function_owner
    USING (true);

CREATE FUNCTION payment.expire_due_approvals(p_now timestamptz, p_limit integer)
RETURNS integer
LANGUAGE plpgsql SECURITY DEFINER VOLATILE PARALLEL UNSAFE
SET search_path = pg_catalog, payment, audit, pg_temp
AS $$
DECLARE v_approval record; v_changed integer; v_expired integer := 0;
BEGIN
    IF session_user <> 'approval_expiry_role' OR p_now IS NULL OR p_limit IS NULL OR p_limit < 1 OR p_limit > 500 THEN
        RAISE EXCEPTION 'invalid approval expiry command context' USING ERRCODE = '42501';
    END IF;
    FOR v_approval IN
        SELECT a.id, a.payment_id, a.maker_user_id, a.submitted_for_approval_at, a.expires_at, p.tenant_id, p.branch_id
        FROM payment.payment_approvals a JOIN payment.payments p ON p.id = a.payment_id
        WHERE a.status = 'PENDING_APPROVAL' AND a.expires_at <= p_now
        ORDER BY a.expires_at, a.id
        LIMIT p_limit
        FOR UPDATE OF a SKIP LOCKED
    LOOP
        PERFORM pg_catalog.set_config('app.tenant_id', v_approval.tenant_id::text, true);
        PERFORM pg_catalog.set_config('app.branch_id', COALESCE(v_approval.branch_id::text, ''), true);
        PERFORM pg_catalog.set_config('app.role', 'system_approval_expiry', true);
        UPDATE payment.payment_approvals SET status = 'EXPIRED'
        WHERE id = v_approval.id AND status = 'PENDING_APPROVAL' AND expires_at <= p_now;
        GET DIAGNOSTICS v_changed = ROW_COUNT;
        IF v_changed = 1 THEN
            PERFORM audit.append_command_audit(v_approval.tenant_id, v_approval.branch_id, 'SYSTEM',
                'system_approval_expiry', 'system_approval_expiry', NULL, pg_catalog.uuidv7(), 'PAYMENT_APPROVAL_EXPIRED',
                'PAYMENT_APPROVAL', v_approval.id, v_approval.payment_id, NULL, NULL,
                pg_catalog.jsonb_build_object('approvalId', v_approval.id, 'approvalStatus', 'PENDING_APPROVAL',
                    'makerIdentity', v_approval.maker_user_id, 'checkerIdentity', '',
                    'submittedAt', v_approval.submitted_for_approval_at, 'expiresAt', v_approval.expires_at, 'decidedAt', ''),
                pg_catalog.jsonb_build_object('approvalId', v_approval.id, 'approvalStatus', 'EXPIRED',
                    'makerIdentity', v_approval.maker_user_id, 'checkerIdentity', '',
                    'submittedAt', v_approval.submitted_for_approval_at, 'expiresAt', v_approval.expires_at, 'decidedAt', ''),
                'SUCCESS', pg_catalog.uuidv7(), p_now);
            v_expired := v_expired + 1;
        END IF;
    END LOOP;
    RETURN v_expired;
END;
$$;

ALTER FUNCTION payment.expire_due_approvals(timestamptz, integer) OWNER TO payment_approval_expiry_function_owner;
REVOKE ALL ON FUNCTION payment.expire_due_approvals(timestamptz, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION payment.expire_due_approvals(timestamptz, integer) TO approval_expiry_role;

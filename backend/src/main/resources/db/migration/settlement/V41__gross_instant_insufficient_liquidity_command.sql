-- ADR-N11: settlement owns the rejected attempt evidence; payment owns the corresponding status outcome.

CREATE FUNCTION settlement.record_gross_instant_insufficient_liquidity(
    p_tenant_id uuid, p_attempt_id uuid, p_payment_id uuid, p_command_id uuid,
    p_amount_minor bigint, p_currency char(3), p_occurred_at timestamptz(3)
) RETURNS TABLE (attempt_id uuid, replayed boolean)
LANGUAGE plpgsql SECURITY DEFINER VOLATILE PARALLEL UNSAFE
SET search_path = pg_catalog, settlement, pg_temp
AS $$
DECLARE v_attempt settlement.settlement_attempts%ROWTYPE;
BEGIN
    IF p_tenant_id IS NULL OR p_attempt_id IS NULL OR p_payment_id IS NULL OR p_command_id IS NULL
       OR p_amount_minor <= 0 OR p_currency <> 'EUR' OR p_occurred_at IS NULL
       OR NULLIF(current_setting('app.tenant_id', true), '') IS DISTINCT FROM p_tenant_id::text THEN
        RAISE EXCEPTION 'invalid gross instant insufficient-liquidity attempt command' USING ERRCODE = '22023';
    END IF;
    SELECT * INTO v_attempt FROM settlement.settlement_attempts WHERE id = p_attempt_id FOR UPDATE;
    IF FOUND THEN
        IF v_attempt.tenant_id <> p_tenant_id OR v_attempt.payment_id <> p_payment_id
           OR v_attempt.command_id <> p_command_id OR v_attempt.state <> 'REJECTED'
           OR v_attempt.amount_minor <> p_amount_minor OR v_attempt.currency <> p_currency
           OR v_attempt.reserve_entry_id IS NOT NULL OR v_attempt.post_entry_id IS NOT NULL THEN
            RAISE EXCEPTION 'gross instant insufficient-liquidity attempt conflicts' USING ERRCODE = 'P0001';
        END IF;
        RETURN QUERY SELECT v_attempt.id, true; RETURN;
    END IF;
    INSERT INTO settlement.settlement_attempts (id, tenant_id, payment_id, command_id, state, amount_minor, currency,
        started_at, completed_at)
    VALUES (p_attempt_id, p_tenant_id, p_payment_id, p_command_id, 'REJECTED', p_amount_minor, p_currency,
        p_occurred_at, p_occurred_at);
    INSERT INTO settlement.settlement_attempt_events (settlement_attempt_id, seq, state, at)
    VALUES (p_attempt_id, 1, 'INITIATED', p_occurred_at), (p_attempt_id, 2, 'INSUFFICIENT_LIQUIDITY', p_occurred_at),
        (p_attempt_id, 3, 'REJECTED', p_occurred_at);
    RETURN QUERY SELECT p_attempt_id, false;
END;
$$;

ALTER FUNCTION settlement.record_gross_instant_insufficient_liquidity(uuid, uuid, uuid, uuid, bigint, char(3), timestamptz)
    OWNER TO settlement_gross_instant_function_owner;
REVOKE ALL ON FUNCTION settlement.record_gross_instant_insufficient_liquidity(uuid, uuid, uuid, uuid, bigint, char(3), timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION settlement.record_gross_instant_insufficient_liquidity(uuid, uuid, uuid, uuid, bigint, char(3), timestamptz)
    TO gross_instant_executor_role;

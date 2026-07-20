-- ADR-N11: payment-lifecycle owns the projection and status-report effects of a gross-instant
-- command. The coordinator can EXECUTE these two narrow commands but receives no payment-table DML.

GRANT USAGE ON SCHEMA payment TO payment_gross_instant_function_owner;
GRANT USAGE ON SCHEMA payment TO gross_instant_executor_role;
GRANT SELECT, UPDATE (finality_at, finality_record_id, status, version) ON payment.payments
    TO payment_gross_instant_function_owner;
GRANT SELECT, INSERT ON payment.payment_events TO payment_gross_instant_function_owner;
GRANT SELECT, INSERT ON payment.payment_status_history TO payment_gross_instant_function_owner;
GRANT SELECT, INSERT ON payment.outbox_events TO payment_gross_instant_function_owner;

CREATE UNIQUE INDEX payment_gross_instant_event_command_key
    ON payment.payment_events (payment_id, type, ((payload ->> 'commandId')))
    WHERE type IN ('payment.gross-instant.finality-projected.v1',
                   'payment.gross-instant.insufficient-liquidity.v1');

CREATE UNIQUE INDEX payment_gross_instant_outbox_command_key
    ON payment.outbox_events (aggregate_id, event_type, correlation_id)
    WHERE event_type = 'payment.status.reported.v1';

CREATE FUNCTION payment.project_gross_instant_finality(
    p_tenant_id uuid, p_payment_id uuid, p_command_id uuid, p_finality_record_id uuid,
    p_finality_at timestamptz(3), p_occurred_at timestamptz(3)
) RETURNS TABLE (projection_outcome text, replayed boolean)
LANGUAGE plpgsql SECURITY DEFINER VOLATILE PARALLEL UNSAFE
SET search_path = pg_catalog, payment, pg_temp
AS $$
DECLARE v_payment payment.payments%ROWTYPE; v_event_present boolean; v_outbox_present boolean;
BEGIN
    IF p_tenant_id IS NULL OR p_payment_id IS NULL OR p_command_id IS NULL OR p_finality_record_id IS NULL
       OR p_finality_at IS NULL OR p_occurred_at IS NULL OR p_finality_at <> p_occurred_at
       OR NULLIF(current_setting('app.tenant_id', true), '') IS DISTINCT FROM p_tenant_id::text THEN
        RAISE EXCEPTION 'invalid gross instant finality projection command' USING ERRCODE = '22023';
    END IF;
    SELECT * INTO v_payment FROM payment.payments WHERE id = p_payment_id FOR UPDATE;
    IF NOT FOUND OR v_payment.tenant_id <> p_tenant_id THEN
        RAISE EXCEPTION 'gross instant payment is absent or outside tenant' USING ERRCODE = 'P0001';
    END IF;
    IF v_payment.status <> 'VALIDATED' THEN
        RAISE EXCEPTION 'gross instant finality requires VALIDATED payment' USING ERRCODE = 'P0001';
    END IF;
    IF v_payment.finality_record_id IS NOT NULL
       AND (v_payment.finality_record_id <> p_finality_record_id OR v_payment.finality_at <> p_finality_at) THEN
        RAISE EXCEPTION 'gross instant finality projection conflicts' USING ERRCODE = 'P0001';
    END IF;
    IF v_payment.finality_record_id IS NULL THEN
        UPDATE payment.payments
        SET finality_record_id = p_finality_record_id, finality_at = p_finality_at, version = version + 1
        WHERE id = p_payment_id AND finality_record_id IS NULL;
    END IF;
    INSERT INTO payment.payment_events (payment_id, type, payload, at)
    VALUES (p_payment_id, 'payment.gross-instant.finality-projected.v1',
            pg_catalog.jsonb_build_object('commandId', p_command_id, 'finalityRecordId', p_finality_record_id,
                'finalityAt', p_finality_at, 'isoStatus', 'ACSC'), p_occurred_at)
    ON CONFLICT DO NOTHING;
    INSERT INTO payment.outbox_events (aggregate_id, event_type, payload, correlation_id, created_at)
    VALUES (p_payment_id, 'payment.status.reported.v1',
            pg_catalog.jsonb_build_object('paymentId', p_payment_id, 'commandId', p_command_id,
                'isoStatus', 'ACSC', 'finalityRecordId', p_finality_record_id, 'finalityAt', p_finality_at),
            p_command_id, p_occurred_at)
    ON CONFLICT DO NOTHING;
    SELECT EXISTS (SELECT 1 FROM payment.payment_events
                   WHERE payment_id = p_payment_id AND type = 'payment.gross-instant.finality-projected.v1'
                     AND payload ->> 'commandId' = p_command_id::text),
           EXISTS (SELECT 1 FROM payment.outbox_events
                   WHERE aggregate_id = p_payment_id AND event_type = 'payment.status.reported.v1'
                     AND correlation_id = p_command_id)
    INTO v_event_present, v_outbox_present;
    IF NOT v_event_present OR NOT v_outbox_present THEN
        RAISE EXCEPTION 'gross instant finality projection evidence is incomplete' USING ERRCODE = 'P0001';
    END IF;
    RETURN QUERY SELECT 'PROJECTED'::text, v_payment.finality_record_id IS NOT NULL;
END;
$$;

CREATE FUNCTION payment.record_gross_instant_insufficient_liquidity(
    p_tenant_id uuid, p_payment_id uuid, p_command_id uuid, p_occurred_at timestamptz(3)
) RETURNS TABLE (rejection_outcome text, replayed boolean)
LANGUAGE plpgsql SECURITY DEFINER VOLATILE PARALLEL UNSAFE
SET search_path = pg_catalog, payment, pg_temp
AS $$
DECLARE v_payment payment.payments%ROWTYPE; v_seq integer; v_event_present boolean; v_outbox_present boolean;
BEGIN
    IF p_tenant_id IS NULL OR p_payment_id IS NULL OR p_command_id IS NULL OR p_occurred_at IS NULL
       OR NULLIF(current_setting('app.tenant_id', true), '') IS DISTINCT FROM p_tenant_id::text THEN
        RAISE EXCEPTION 'invalid gross instant insufficient-liquidity command' USING ERRCODE = '22023';
    END IF;
    SELECT * INTO v_payment FROM payment.payments WHERE id = p_payment_id FOR UPDATE;
    IF NOT FOUND OR v_payment.tenant_id <> p_tenant_id THEN
        RAISE EXCEPTION 'gross instant payment is absent or outside tenant' USING ERRCODE = 'P0001';
    END IF;
    IF v_payment.status NOT IN ('VALIDATED', 'REJECTED') OR v_payment.finality_record_id IS NOT NULL THEN
        RAISE EXCEPTION 'gross instant insufficient-liquidity state conflicts' USING ERRCODE = 'P0001';
    END IF;
    IF v_payment.status = 'VALIDATED' THEN
        UPDATE payment.payments SET status = 'REJECTED', version = version + 1 WHERE id = p_payment_id;
        SELECT COALESCE(max(seq), 0) + 1 INTO v_seq FROM payment.payment_status_history WHERE payment_id = p_payment_id;
        INSERT INTO payment.payment_status_history (payment_id, seq, from_status, to_status, status_code,
            reason_code, source_type, actor_type, is_final, event_type, event_ref, at)
        VALUES (p_payment_id, v_seq, 'VALIDATED', 'REJECTED', 'RJCT', 'INSUFFICIENT_LIQUIDITY',
            'SETTLEMENT', 'SYSTEM', false, 'payment.gross-instant.insufficient-liquidity.v1', p_command_id, p_occurred_at);
    END IF;
    INSERT INTO payment.payment_events (payment_id, type, payload, at)
    VALUES (p_payment_id, 'payment.gross-instant.insufficient-liquidity.v1',
            pg_catalog.jsonb_build_object('commandId', p_command_id, 'isoStatus', 'RJCT',
                'reasonCode', 'INSUFFICIENT_LIQUIDITY'), p_occurred_at)
    ON CONFLICT DO NOTHING;
    INSERT INTO payment.outbox_events (aggregate_id, event_type, payload, correlation_id, created_at)
    VALUES (p_payment_id, 'payment.status.reported.v1',
            pg_catalog.jsonb_build_object('paymentId', p_payment_id, 'commandId', p_command_id,
                'isoStatus', 'RJCT', 'reasonCode', 'INSUFFICIENT_LIQUIDITY'), p_command_id, p_occurred_at)
    ON CONFLICT DO NOTHING;
    SELECT EXISTS (SELECT 1 FROM payment.payment_events
                   WHERE payment_id = p_payment_id AND type = 'payment.gross-instant.insufficient-liquidity.v1'
                     AND payload ->> 'commandId' = p_command_id::text),
           EXISTS (SELECT 1 FROM payment.outbox_events
                   WHERE aggregate_id = p_payment_id AND event_type = 'payment.status.reported.v1'
                     AND correlation_id = p_command_id)
    INTO v_event_present, v_outbox_present;
    IF NOT v_event_present OR NOT v_outbox_present THEN
        RAISE EXCEPTION 'gross instant insufficient-liquidity evidence is incomplete' USING ERRCODE = 'P0001';
    END IF;
    RETURN QUERY SELECT 'REJECTED'::text, v_payment.status = 'REJECTED';
END;
$$;

ALTER FUNCTION payment.project_gross_instant_finality(uuid, uuid, uuid, uuid, timestamptz, timestamptz)
    OWNER TO payment_gross_instant_function_owner;
ALTER FUNCTION payment.record_gross_instant_insufficient_liquidity(uuid, uuid, uuid, timestamptz)
    OWNER TO payment_gross_instant_function_owner;
REVOKE ALL ON FUNCTION payment.project_gross_instant_finality(uuid, uuid, uuid, uuid, timestamptz, timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION payment.record_gross_instant_insufficient_liquidity(uuid, uuid, uuid, timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION payment.project_gross_instant_finality(uuid, uuid, uuid, uuid, timestamptz, timestamptz)
    TO gross_instant_executor_role;
GRANT EXECUTE ON FUNCTION payment.record_gross_instant_insufficient_liquidity(uuid, uuid, uuid, timestamptz)
    TO gross_instant_executor_role;

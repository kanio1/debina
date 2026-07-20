-- owner: ledger — ADR-N11 forward fix for V36
-- V36 was already applied in PostgreSQL Testcontainers verification. This append-only replacement
-- preserves its command contract while acquiring the two liquidity rows in UUID order.

CREATE OR REPLACE FUNCTION ledger.gross_instant_reserve_post(
    p_tenant_id uuid, p_settlement_attempt_id uuid, p_payment_id uuid,
    p_debtor_account_id uuid, p_creditor_account_id uuid, p_amount_minor bigint,
    p_currency char(3), p_command_id uuid, p_occurred_at timestamptz(3)
) RETURNS TABLE (outcome text, reservation_id uuid, terminal_entry_id uuid,
    terminal_occurred_at timestamptz(3), replayed boolean)
LANGUAGE plpgsql SECURITY DEFINER VOLATILE PARALLEL UNSAFE
SET search_path = pg_catalog, ledger, pg_temp
AS $$
DECLARE
    v_reservation ledger.reservations%ROWTYPE;
    v_debtor ledger.liquidity_accounts%ROWTYPE;
    v_creditor ledger.liquidity_accounts%ROWTYPE;
    v_account record;
    v_reserve_entry uuid;
    v_post_entry uuid;
BEGIN
    IF p_tenant_id IS NULL OR p_settlement_attempt_id IS NULL OR p_payment_id IS NULL
       OR p_debtor_account_id IS NULL OR p_creditor_account_id IS NULL OR p_command_id IS NULL
       OR p_occurred_at IS NULL OR p_amount_minor IS NULL OR p_amount_minor <= 0 OR p_currency IS NULL THEN
        RAISE EXCEPTION 'gross instant ledger command requires identifiers, positive amount, currency and time'
            USING ERRCODE = '22023';
    END IF;
    IF p_debtor_account_id = p_creditor_account_id THEN
        RAISE EXCEPTION 'gross instant debtor and creditor accounts must differ' USING ERRCODE = '22023';
    END IF;
    SELECT * INTO v_reservation FROM ledger.reservations
      WHERE settlement_attempt_id = p_settlement_attempt_id FOR UPDATE;
    IF FOUND THEN
        IF v_reservation.payment_id <> p_payment_id OR v_reservation.debtor_account_id <> p_debtor_account_id
           OR v_reservation.amount_minor <> p_amount_minor OR v_reservation.currency <> p_currency THEN
            RAISE EXCEPTION 'gross instant reservation command conflicts with existing attempt' USING ERRCODE = 'P0001';
        END IF;
        IF v_reservation.state = 'POSTED' AND v_reservation.terminal_command_id = p_command_id THEN
            RETURN QUERY SELECT 'POSTED'::text, v_reservation.id, v_reservation.terminal_entry_id,
                (SELECT created_at FROM ledger.journal_entries WHERE id = v_reservation.terminal_entry_id), true;
            RETURN;
        END IF;
        RAISE EXCEPTION 'gross instant reservation is terminal or command conflicts' USING ERRCODE = 'P0001';
    END IF;
    FOR v_account IN
        SELECT * FROM ledger.liquidity_accounts
         WHERE id IN (p_debtor_account_id, p_creditor_account_id) AND tenant_id = p_tenant_id
         ORDER BY id FOR UPDATE
    LOOP
        IF v_account.id = p_debtor_account_id THEN v_debtor := v_account; ELSE v_creditor := v_account; END IF;
    END LOOP;
    IF v_debtor.id IS NULL OR v_creditor.id IS NULL THEN
        RAISE EXCEPTION 'gross instant account is missing or belongs to another tenant' USING ERRCODE = '42501';
    END IF;
    SELECT * INTO v_reservation FROM ledger.reservations
      WHERE settlement_attempt_id = p_settlement_attempt_id FOR UPDATE;
    IF FOUND THEN RAISE EXCEPTION 'gross instant concurrent attempt must replay after retry' USING ERRCODE = '40001'; END IF;
    IF v_debtor.currency <> p_currency OR v_creditor.currency <> p_currency THEN
        RAISE EXCEPTION 'gross instant account currency conflicts with command currency' USING ERRCODE = '22023';
    END IF;
    IF v_debtor.available_minor < p_amount_minor THEN
        RETURN QUERY SELECT 'INSUFFICIENT_LIQUIDITY'::text, NULL::uuid, NULL::uuid, NULL::timestamptz, false;
        RETURN;
    END IF;
    v_reserve_entry := gen_random_uuid();
    INSERT INTO ledger.journal_entries (id, entry_type, payment_id, business_date, created_at)
    VALUES (v_reserve_entry, 'RESERVE', p_payment_id, p_occurred_at::date, p_occurred_at);
    INSERT INTO ledger.reservations (id, settlement_attempt_id, payment_id, debtor_account_id, amount_minor,
        currency, state, reserve_entry_id)
    VALUES (gen_random_uuid(), p_settlement_attempt_id, p_payment_id, p_debtor_account_id, p_amount_minor,
        p_currency, 'ACTIVE', v_reserve_entry) RETURNING * INTO v_reservation;
    INSERT INTO ledger.journal_lines (entry_id, line_no, account_id, currency, amount_minor, balance_component, at)
    VALUES (v_reserve_entry, 1, p_debtor_account_id, p_currency, -p_amount_minor, 'AVAILABLE', p_occurred_at),
           (v_reserve_entry, 2, p_debtor_account_id, p_currency, p_amount_minor, 'RESERVED', p_occurred_at);
    UPDATE ledger.liquidity_accounts SET available_minor = available_minor - p_amount_minor,
        reserved_minor = reserved_minor + p_amount_minor, version = version + 1 WHERE id = p_debtor_account_id;
    v_post_entry := gen_random_uuid();
    INSERT INTO ledger.journal_entries (id, entry_type, payment_id, business_date, created_at)
    VALUES (v_post_entry, 'POST', p_payment_id, p_occurred_at::date, p_occurred_at);
    INSERT INTO ledger.journal_lines (entry_id, line_no, account_id, currency, amount_minor, balance_component, at)
    VALUES (v_post_entry, 1, p_debtor_account_id, p_currency, -p_amount_minor, 'RESERVED', p_occurred_at),
           (v_post_entry, 2, p_creditor_account_id, p_currency, p_amount_minor, 'AVAILABLE', p_occurred_at);
    UPDATE ledger.liquidity_accounts SET reserved_minor = reserved_minor - p_amount_minor, version = version + 1
      WHERE id = p_debtor_account_id;
    UPDATE ledger.liquidity_accounts SET available_minor = available_minor + p_amount_minor, version = version + 1
      WHERE id = p_creditor_account_id;
    UPDATE ledger.reservations SET state = 'POSTED', terminal_entry_id = v_post_entry,
        terminal_command_id = p_command_id, completed_at = p_occurred_at, version = version + 1
      WHERE id = v_reservation.id AND state = 'ACTIVE';
    IF NOT FOUND THEN RAISE EXCEPTION 'gross instant reservation state changed unexpectedly' USING ERRCODE = '40001'; END IF;
    RETURN QUERY SELECT 'POSTED'::text, v_reservation.id, v_post_entry, p_occurred_at, false;
END;
$$;

ALTER FUNCTION ledger.gross_instant_reserve_post(uuid, uuid, uuid, uuid, uuid, bigint, char(3), uuid, timestamptz)
    OWNER TO ledger_gross_instant_function_owner;
REVOKE ALL ON FUNCTION ledger.gross_instant_reserve_post(uuid, uuid, uuid, uuid, uuid, bigint, char(3), uuid, timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION ledger.gross_instant_reserve_post(uuid, uuid, uuid, uuid, uuid, bigint, char(3), uuid, timestamptz)
    TO gross_instant_executor_role;

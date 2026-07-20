-- owner: settlement — ADR-N11
-- Additive runtime attempt evidence and a single, explicit ON_LEDGER_POST command.

CREATE TABLE settlement.settlement_attempts (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    payment_id uuid NOT NULL UNIQUE,
    command_id uuid NOT NULL UNIQUE,
    state text NOT NULL CHECK (state IN ('FINAL', 'REJECTED')),
    reserve_entry_id uuid,
    post_entry_id uuid,
    amount_minor bigint NOT NULL CHECK (amount_minor > 0),
    currency char(3) NOT NULL,
    started_at timestamptz(3) NOT NULL,
    completed_at timestamptz(3) NOT NULL
);
CREATE TABLE settlement.settlement_attempt_events (
    attempt_id uuid NOT NULL REFERENCES settlement.settlement_attempts(id),
    seq integer NOT NULL,
    state text NOT NULL,
    at timestamptz(3) NOT NULL,
    PRIMARY KEY (attempt_id, seq)
);
ALTER TABLE settlement.settlement_attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE settlement.settlement_attempts FORCE ROW LEVEL SECURITY;
CREATE POLICY settlement_attempt_tenant_isolation ON settlement.settlement_attempts
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT USAGE ON SCHEMA settlement, reference_data TO settlement_gross_instant_function_owner, gross_instant_executor_role;
GRANT SELECT, INSERT ON settlement.settlement_attempts, settlement.settlement_attempt_events,
    settlement.settlement_profile_snapshots, settlement.settlement_finality_records
    TO settlement_gross_instant_function_owner;
GRANT SELECT ON reference_data.finality_rules TO settlement_gross_instant_function_owner;

CREATE FUNCTION settlement.record_gross_instant_post(
    p_tenant_id uuid, p_attempt_id uuid, p_payment_id uuid, p_command_id uuid,
    p_reserve_entry_id uuid, p_post_entry_id uuid, p_amount_minor bigint, p_currency char(3),
    p_occurred_at timestamptz(3), p_evidence_hash bytea
) RETURNS TABLE (finality_record_id uuid, finality_at timestamptz(3), replayed boolean)
LANGUAGE plpgsql SECURITY DEFINER VOLATILE PARALLEL UNSAFE
SET search_path = pg_catalog, settlement, reference_data, pg_temp
AS $$
DECLARE v_attempt settlement.settlement_attempts%ROWTYPE; v_snapshot uuid; v_record settlement.settlement_finality_records%ROWTYPE;
BEGIN
    IF p_tenant_id IS NULL OR p_attempt_id IS NULL OR p_payment_id IS NULL OR p_command_id IS NULL
       OR p_reserve_entry_id IS NULL OR p_post_entry_id IS NULL OR p_amount_minor IS NULL OR p_amount_minor <= 0
       OR p_currency IS NULL OR p_occurred_at IS NULL OR p_evidence_hash IS NULL OR octet_length(p_evidence_hash) = 0 THEN
        RAISE EXCEPTION 'gross instant settlement post command requires complete evidence' USING ERRCODE = '22023';
    END IF;
    IF NULLIF(current_setting('app.tenant_id', true), '')::uuid IS DISTINCT FROM p_tenant_id THEN
        RAISE EXCEPTION 'gross instant tenant context does not match command' USING ERRCODE = '42501';
    END IF;
    SELECT * INTO v_attempt FROM settlement.settlement_attempts WHERE id = p_attempt_id FOR UPDATE;
    IF FOUND THEN
        IF v_attempt.command_id <> p_command_id OR v_attempt.payment_id <> p_payment_id OR v_attempt.state <> 'FINAL' THEN
            RAISE EXCEPTION 'gross instant settlement attempt conflicts' USING ERRCODE = 'P0001';
        END IF;
        SELECT * INTO v_record FROM settlement.settlement_finality_records WHERE payment_id = p_payment_id;
        IF NOT FOUND OR v_record.source_id <> p_post_entry_id OR NOT pg_catalog.digest(v_record.evidence_hash, 'sha256') = pg_catalog.digest(p_evidence_hash, 'sha256') THEN
            RAISE EXCEPTION 'gross instant finality evidence conflicts' USING ERRCODE = 'P0001';
        END IF;
        RETURN QUERY SELECT v_record.id, v_record.finality_at, true; RETURN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM reference_data.finality_rules WHERE finality_rule_code = 'ON_LEDGER_POST' AND finality_rule_version = 1) THEN
        RAISE EXCEPTION 'ON_LEDGER_POST finality rule is absent' USING ERRCODE = 'P0001';
    END IF;
    INSERT INTO settlement.settlement_attempts VALUES (p_attempt_id, p_tenant_id, p_payment_id, p_command_id,
        'FINAL', p_reserve_entry_id, p_post_entry_id, p_amount_minor, p_currency, p_occurred_at, p_occurred_at);
    INSERT INTO settlement.settlement_attempt_events VALUES
        (p_attempt_id, 1, 'INITIATED', p_occurred_at), (p_attempt_id, 2, 'RESERVED', p_occurred_at),
        (p_attempt_id, 3, 'POSTED', p_occurred_at), (p_attempt_id, 4, 'FINAL', p_occurred_at);
    INSERT INTO settlement.settlement_profile_snapshots (id, settlement_attempt_id, finality_rule_code, finality_rule_version)
    VALUES (gen_random_uuid(), p_attempt_id, 'ON_LEDGER_POST', 1) RETURNING id INTO v_snapshot;
    INSERT INTO settlement.settlement_finality_records (id, payment_id, settlement_attempt_id, profile_snapshot_id,
        finality_rule_code, finality_rule_version, source_type, source_id, source_occurred_at, finality_at, evidence_hash)
    VALUES (gen_random_uuid(), p_payment_id, p_attempt_id, v_snapshot, 'ON_LEDGER_POST', 1, 'LEDGER_ENTRY',
        p_post_entry_id, p_occurred_at, p_occurred_at, p_evidence_hash) RETURNING * INTO v_record;
    RETURN QUERY SELECT v_record.id, v_record.finality_at, false;
END;
$$;
ALTER FUNCTION settlement.record_gross_instant_post(uuid, uuid, uuid, uuid, uuid, uuid, bigint, char(3), timestamptz, bytea)
    OWNER TO settlement_gross_instant_function_owner;
REVOKE ALL ON FUNCTION settlement.record_gross_instant_post(uuid, uuid, uuid, uuid, uuid, uuid, bigint, char(3), timestamptz, bytea) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION settlement.record_gross_instant_post(uuid, uuid, uuid, uuid, uuid, uuid, bigint, char(3), timestamptz, bytea)
    TO gross_instant_executor_role;

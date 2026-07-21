-- owner: settlement — blueprint §4.6/§4.11, ADR-N13, EPIC-34/37
--
-- Migration impact
-- Schemat/właściciel: settlement / settlement_role
-- Tabele: new deferred_cycle_attempts, deferred_cycle_attempt_events, settlement_cycles,
--   settlement_items and settlement_positions
-- Obecna najwyższa migracja: V50
-- Czy migracja była już stosowana: no
-- Przewidywany lock: new tables, indexes and grants only; no existing-table rewrite/backfill.
-- Ryzyko wielkości tabeli: empty additive tables; the unique/index constraints serve G6 and P8 lookup paths.
-- Backward compatibility: additive; routing V45--V50 data is untouched.
-- Expand/contract: expand only.
-- RLS/grants impact: selective-RLS architecture leaves settlement operational tables grant-protected;
--   settlement_role is their sole writer and gains no ledger/payment privilege.
-- Forward-fix: append-only migration.
-- Fresh DB verification: DeferredSettlementCycleIntegrationTest.
-- Upgrade verification: DeferredSettlementCycleMigrationUpgradePathTest.

CREATE TABLE settlement.deferred_cycle_attempts (
    id uuid PRIMARY KEY,
    payment_id uuid NOT NULL UNIQUE,
    profile_id uuid NOT NULL,
    settlement_basis text NOT NULL CHECK (settlement_basis = 'NET_DEFERRED'),
    liquidity_mode text NOT NULL CHECK (liquidity_mode = 'ISOLATED_SUBACCOUNT'),
    state text NOT NULL CHECK (state IN ('INITIATED', 'ACCEPTED', 'CYCLE_ASSIGNED', 'FINAL', 'REJECTED', 'SETTLEMENT_FAILED_TECHNICAL')),
    created_at timestamptz(3) NOT NULL DEFAULT now()
);

CREATE TABLE settlement.deferred_cycle_attempt_events (
    settlement_attempt_id uuid NOT NULL REFERENCES settlement.deferred_cycle_attempts (id),
    seq integer NOT NULL CHECK (seq > 0),
    state text NOT NULL CHECK (state IN ('CYCLE_ASSIGNED', 'FINAL', 'SETTLEMENT_FAILED_TECHNICAL')),
    at timestamptz(3) NOT NULL,
    PRIMARY KEY (settlement_attempt_id, seq)
);

CREATE TABLE settlement.settlement_cycles (
    id uuid PRIMARY KEY,
    profile_id uuid NOT NULL,
    business_date date NOT NULL,
    session_no smallint NOT NULL CHECK (session_no >= 0),
    state text NOT NULL CHECK (state IN ('OPEN', 'CLOSING', 'CLOSED', 'NETTED', 'SETTLED', 'RECONCILED')),
    cutoff_at timestamptz(3) NOT NULL,
    closed_at timestamptz(3),
    netted_at timestamptz(3),
    settled_at timestamptz(3),
    created_at timestamptz(3) NOT NULL DEFAULT now(),
    UNIQUE (profile_id, business_date, session_no)
);

CREATE TABLE settlement.settlement_items (
    id uuid PRIMARY KEY,
    cycle_id uuid NOT NULL REFERENCES settlement.settlement_cycles (id),
    settlement_attempt_id uuid NOT NULL UNIQUE REFERENCES settlement.deferred_cycle_attempts (id),
    payment_id uuid NOT NULL,
    debtor_participant_id uuid NOT NULL,
    creditor_participant_id uuid NOT NULL,
    amount_minor bigint NOT NULL CHECK (amount_minor > 0),
    UNIQUE (cycle_id, payment_id)
);

CREATE TABLE settlement.settlement_positions (
    cycle_id uuid NOT NULL REFERENCES settlement.settlement_cycles (id),
    participant_id uuid NOT NULL,
    net_minor bigint NOT NULL,
    PRIMARY KEY (cycle_id, participant_id)
);

CREATE TABLE settlement.settlement_cycle_command_receipts (
    command_id uuid PRIMARY KEY,
    cycle_id uuid NOT NULL REFERENCES settlement.settlement_cycles (id),
    command_type text NOT NULL CHECK (command_type IN ('BEGIN_CLOSING', 'CLOSE', 'NET', 'SETTLE')),
    recorded_at timestamptz(3) NOT NULL DEFAULT now()
);

CREATE INDEX settlement_items_cycle_idx ON settlement.settlement_items (cycle_id);

REVOKE ALL ON settlement.deferred_cycle_attempts, settlement.deferred_cycle_attempt_events,
    settlement.settlement_cycles, settlement.settlement_items, settlement.settlement_positions,
    settlement.settlement_cycle_command_receipts FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE ON settlement.deferred_cycle_attempts, settlement.settlement_cycles TO settlement_role;
GRANT SELECT, INSERT ON settlement.deferred_cycle_attempt_events, settlement.settlement_items,
    settlement.settlement_positions, settlement.settlement_cycle_command_receipts TO settlement_role;

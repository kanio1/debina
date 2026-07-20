-- owner: ledger — ADR-N10, EPIC-32 Story 32.2
--
-- Migration impact
-- Schemat/właściciel: ledger / ledger_role
-- Tabele: ledger.journal_lines (new component), new ledger.reservations
-- Obecna najwyższa migracja: V33
-- Czy migracja była już stosowana: no
-- Przewidywany lock: ALTER TABLE takes a brief ACCESS EXCLUSIVE lock; PostgreSQL stores the constant
--   DEFAULT without a table rewrite. CREATE TABLE/INDEX/TRIGGER affects only new objects.
-- Ryzyko wielkości tabeli: existing journal lines receive the explicit compatibility component
--   AVAILABLE; new reserve/release lines state their component directly.
-- Backward compatibility: valid existing journal evidence survives unchanged apart from its explicit
--   AVAILABLE classification; no money row is deleted or recalculated.
-- Expand/contract: expand only.
-- RLS/grants impact: ledger remains no-RLS; ledger_role gains only the reservation operations it owns;
--   settlement_role receives no ledger grant.
-- Forward-fix: append-only migration.
-- Fresh DB verification: LedgerReservationSchemaMigrationTest.
-- Upgrade verification: LedgerReservationMigrationUpgradePathTest.

ALTER TABLE ledger.journal_lines
    ADD COLUMN balance_component text NOT NULL DEFAULT 'AVAILABLE'
        CHECK (balance_component IN ('AVAILABLE', 'RESERVED'));

CREATE TABLE ledger.reservations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_attempt_id uuid NOT NULL UNIQUE,
    payment_id uuid NOT NULL,
    debtor_account_id uuid NOT NULL,
    amount_minor bigint NOT NULL CHECK (amount_minor > 0),
    currency char(3) NOT NULL,
    state text NOT NULL CHECK (state IN ('ACTIVE', 'POSTED', 'RELEASED')),
    reserve_entry_id uuid NOT NULL UNIQUE REFERENCES ledger.journal_entries (id),
    terminal_entry_id uuid UNIQUE REFERENCES ledger.journal_entries (id),
    terminal_command_id uuid UNIQUE,
    version integer NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz(3) NOT NULL DEFAULT now(),
    completed_at timestamptz(3),
    CONSTRAINT ledger_reservation_account_currency_fk
        FOREIGN KEY (debtor_account_id, currency)
        REFERENCES ledger.liquidity_accounts (id, currency),
    CONSTRAINT ledger_reservation_active_shape_check
        CHECK (
            (state = 'ACTIVE' AND terminal_entry_id IS NULL AND terminal_command_id IS NULL AND completed_at IS NULL)
            OR
            (state IN ('POSTED', 'RELEASED') AND terminal_entry_id IS NOT NULL
                AND terminal_command_id IS NOT NULL AND completed_at IS NOT NULL)
        )
);

CREATE OR REPLACE FUNCTION ledger.guard_reservation_transition() RETURNS trigger AS $$
BEGIN
    IF OLD.state <> 'ACTIVE' THEN
        RAISE EXCEPTION 'reservation % is already terminal (%); a second terminal transition is forbidden',
            OLD.id, OLD.state;
    END IF;
    IF NEW.state NOT IN ('POSTED', 'RELEASED') THEN
        RAISE EXCEPTION 'reservation % may transition only from ACTIVE to POSTED or RELEASED', OLD.id;
    END IF;
    IF NEW.reserve_entry_id <> OLD.reserve_entry_id
        OR NEW.settlement_attempt_id <> OLD.settlement_attempt_id
        OR NEW.payment_id <> OLD.payment_id
        OR NEW.debtor_account_id <> OLD.debtor_account_id
        OR NEW.amount_minor <> OLD.amount_minor
        OR NEW.currency <> OLD.currency THEN
        RAISE EXCEPTION 'reservation % immutable identity changed', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_reservations_one_terminal_transition
    BEFORE UPDATE ON ledger.reservations
    FOR EACH ROW EXECUTE FUNCTION ledger.guard_reservation_transition();

REVOKE ALL ON ledger.reservations FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE ON ledger.reservations TO ledger_role;

-- owner: ledger [MVP] — sepa-nexus-message-flow-and-data-blueprint.md §4.5/§4.7, EPIC-32 Story 32.1
--
-- Scope decision: this migration builds the DDL + grant foundation only —
-- LedgerPort.reserve/post/release (Story 32.2), the deferred balance trigger + immutability +
-- reversal-flow tests (Story 32.3/32.4) build on top of this, not duplicated here. The trigger
-- itself IS created here because §4.5 frames it as inseparable from journal_lines' own definition
-- ("a SQL-level invariant, not a Java convention") — creating the table without it would leave a
-- table that can silently accept unbalanced entries, which is worse than not having the table yet.
--
-- Reversal model — planning correction, not a new design decision: Story 32.1's original task
-- text (planning/epics/EPIC-32-ledger-core.md) named a `ledger.ledger_reversals` table, copying
-- language from sepa-nexus-blueprint-ownership-integration.md's ownership tables (lines 58/79/136/
-- 233), which list "ledger_reversals" as one of ledger's owned tables. But the canonical DDL
-- source for this table's actual shape — sepa-nexus-message-flow-and-data-blueprint.md §4.5,
-- explicitly marked "[PATCH v2, deep-research applied]", i.e. the most recent, most authoritative
-- version — defines reversal without any separate table: a reversal is a new journal_entries row
-- with entry_type='REVERSAL' and reversal_of_entry_id referencing the original ("the ONLY
-- correction mechanism"). No document anywhere shows actual CREATE TABLE DDL for a standalone
-- ledger_reversals table; the ownership doc's mentions are bare table-name references in an
-- ownership map, never backed by a schema. Per this session's own explicit decision rule for
-- exactly this scenario: no ledger.ledger_reversals is created; the corrected task is recorded in
-- the epic file.
--
-- Partitioning — §4.5's DDL sketch specifies `journal_lines ... PARTITION BY RANGE (at)`, but this
-- repository already has an established, documented precedent for exactly this situation:
-- payment.payment_events (V19) and ingress.raw_inbound_messages (V10) both deliberately built
-- their own PARTITION BY RANGE-sketched source tables as plain, non-partitioned tables, because no
-- source document resolves an operational partition-lifecycle strategy (who pre-creates the next
-- partition, what happens at a boundary) — partitioning is documented as "a later DBA-hardening
-- pass", not required to prove the append-only/balance-invariant contract this story is actually
-- about. journal_lines follows the identical precedent here, for the identical reason.

CREATE TABLE ledger.liquidity_accounts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    branch_id uuid,
    participant_id uuid NOT NULL,
    currency char(3) NOT NULL,
    available_minor bigint NOT NULL,
    reserved_minor bigint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CHECK (available_minor >= 0),
    CHECK (reserved_minor >= 0),
    UNIQUE (participant_id, currency)
) WITH (fillfactor = 80);

CREATE TABLE ledger.journal_entries (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_type text NOT NULL
        CHECK (entry_type IN ('RESERVE', 'POST', 'RELEASE', 'NETTING_BATCH', 'REVERSAL')),
    payment_id uuid,
    cycle_id uuid,
    business_date date NOT NULL,
    entry_status text NOT NULL DEFAULT 'POSTED'
        CHECK (entry_status IN ('POSTED', 'REVERSED')),
    reversal_of_entry_id uuid REFERENCES ledger.journal_entries (id),
    created_at timestamptz(3) NOT NULL DEFAULT now()
);

CREATE TABLE ledger.journal_lines (
    entry_id uuid NOT NULL REFERENCES ledger.journal_entries (id),
    line_no smallint NOT NULL,
    account_id uuid NOT NULL,
    currency char(3) NOT NULL,
    amount_minor bigint NOT NULL,
    at timestamptz(3) NOT NULL,
    PRIMARY KEY (at, entry_id, line_no)
) WITH (fillfactor = 100);

CREATE INDEX jl_entry_idx ON ledger.journal_lines (entry_id);
CREATE INDEX jl_account_at_idx ON ledger.journal_lines (account_id, at DESC);

-- SQL-level invariant (§4.5): Sigma(journal_lines.amount_minor) = 0 per entry_id, enforced by a
-- deferred constraint trigger firing at COMMIT — never assumed from application discipline alone.
-- A multi-statement transaction can insert lines for one entry one at a time and still be checked
-- atomically as a whole.
CREATE OR REPLACE FUNCTION ledger.check_entry_balance() RETURNS trigger AS $$
DECLARE unbalanced RECORD;
BEGIN
    SELECT entry_id, sum(amount_minor) AS total INTO unbalanced
    FROM ledger.journal_lines WHERE entry_id = NEW.entry_id
    GROUP BY entry_id HAVING sum(amount_minor) <> 0;
    IF FOUND THEN
        RAISE EXCEPTION 'journal_entry % does not balance (sum=%)', unbalanced.entry_id, unbalanced.total;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_entry_balance
    AFTER INSERT ON ledger.journal_lines
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ledger.check_entry_balance();

CREATE TABLE ledger.balance_snapshots (
    account_id uuid NOT NULL,
    snapshot_at timestamptz(3) NOT NULL,
    available_minor bigint NOT NULL,
    reserved_minor bigint NOT NULL,
    through_entry_id uuid NOT NULL,
    PRIMARY KEY (account_id, snapshot_at)
);

-- Grants (§4.7's own SQL example, applied verbatim to the now-real ledger schema): NO RLS on any
-- ledger base table — table ownership is the boundary. journal_entries/journal_lines get
-- SELECT+INSERT only, never UPDATE/DELETE — the only correction mechanism is a new REVERSAL entry
-- (Story 32.4). liquidity_accounts gets SELECT+UPDATE (current-balance updates are legitimate) —
-- source does not grant INSERT here; account provisioning is not part of this story's scope.
REVOKE ALL ON ledger.journal_entries, ledger.journal_lines, ledger.liquidity_accounts FROM PUBLIC;
GRANT SELECT, INSERT ON ledger.journal_entries, ledger.journal_lines TO ledger_role;
GRANT SELECT, UPDATE ON ledger.liquidity_accounts TO ledger_role;

REVOKE ALL ON ledger.balance_snapshots FROM PUBLIC;
GRANT SELECT, INSERT ON ledger.balance_snapshots TO ledger_role;

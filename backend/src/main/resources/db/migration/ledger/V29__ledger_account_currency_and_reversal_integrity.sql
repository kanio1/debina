-- owner: ledger — EPIC-32 Story 32.5; DATA-GAP-001/DATA-GAP-002
--
-- Migration impact
-- Schemat/właściciel: ledger / ledger_role
-- Tabele: liquidity_accounts, journal_entries, journal_lines
-- Obecna najwyższa migracja: V28
-- Czy migracja była już stosowana: no; forward fix for V26
-- Przewidywany lock: ALTER TABLE takes brief ACCESS EXCLUSIVE locks; no table rewrite.
-- Ryzyko wielkości tabeli: ledger schema is newly introduced; constraints scan existing rows.
-- Backward compatibility: valid pre-V29 rows remain valid; invalid evidence stops the upgrade.
-- Expand/contract: constraint-only forward fix; no data deletion or backfill.
-- RLS/grants impact: none; base ledger tables intentionally have no RLS.
-- Forward-fix: new append-only migration, never edits V26.
-- Fresh DB verification: JournalAccountCurrencyIntegrityTest/ReversalStructuralIntegrityTest.
-- Upgrade verification: LedgerIntegrityMigrationUpgradePathTest.

ALTER TABLE ledger.liquidity_accounts
    ADD CONSTRAINT liquidity_accounts_id_currency_key UNIQUE (id, currency);

ALTER TABLE ledger.journal_lines
    ADD CONSTRAINT journal_lines_account_currency_fk
    FOREIGN KEY (account_id, currency)
    REFERENCES ledger.liquidity_accounts (id, currency);

CREATE OR REPLACE FUNCTION ledger.check_entry_balance() RETURNS trigger AS $$
DECLARE invalid_entry RECORD;
BEGIN
    SELECT entry_id, count(DISTINCT currency) AS currency_count, sum(amount_minor) AS total
    INTO invalid_entry
    FROM ledger.journal_lines
    WHERE entry_id = NEW.entry_id
    GROUP BY entry_id
    HAVING count(DISTINCT currency) <> 1 OR sum(amount_minor) <> 0;
    IF FOUND THEN
        RAISE EXCEPTION 'journal_entry % does not balance or use one currency (currencies=%, sum=%)',
            invalid_entry.entry_id, invalid_entry.currency_count, invalid_entry.total;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

ALTER TABLE ledger.journal_entries
    ADD CONSTRAINT journal_entries_reversal_pointer_check
    CHECK ((entry_type = 'REVERSAL') = (reversal_of_entry_id IS NOT NULL)),
    ADD CONSTRAINT journal_entries_no_self_reversal_check
    CHECK (reversal_of_entry_id IS NULL OR reversal_of_entry_id <> id);

CREATE UNIQUE INDEX journal_entries_one_reversal_per_original_idx
    ON ledger.journal_entries (reversal_of_entry_id)
    WHERE entry_type = 'REVERSAL';

-- owner: reference-data — ADR-N10, EPIC-39 Story 39.1
--
-- Migration impact
-- Schemat/właściciel: reference_data / reference_data_role
-- Tabele: new reference_data.finality_rules
-- Obecna najwyższa migracja: V30
-- Czy migracja była już stosowana: no
-- Przewidywany lock: CREATE TABLE only; no existing-table lock or rewrite.
-- Ryzyko wielkości tabeli: new, deliberately small versioned catalog.
-- Backward compatibility: additive; no existing rule/profile is reinterpreted.
-- Expand/contract: expand only.
-- RLS/grants impact: existing reference-data writer/read grants apply; no new foreign writer.
-- Forward-fix: append-only migration.
-- Fresh DB verification: FinalitySchemaMigrationTest.
-- Upgrade verification: FinalityMigrationUpgradePathTest.

CREATE TABLE reference_data.finality_rules (
    finality_rule_code text NOT NULL CHECK (finality_rule_code IN (
        'ON_LEDGER_POST',
        'ON_CYCLE_SETTLED',
        'ON_NET_POSITION_SETTLED',
        'ON_INTERNAL_BOOK_POST'
    )),
    finality_rule_version integer NOT NULL CHECK (finality_rule_version > 0),
    created_at timestamptz(3) NOT NULL DEFAULT now(),
    PRIMARY KEY (finality_rule_code, finality_rule_version)
);

-- ADR-N10's laboratory catalog is explicit and versioned. ON_NET_POSITION_SETTLED is represented
-- now for policy selection only; no synthetic trigger is invented until its settlement flow exists.
INSERT INTO reference_data.finality_rules (finality_rule_code, finality_rule_version)
VALUES
    ('ON_LEDGER_POST', 1),
    ('ON_CYCLE_SETTLED', 1),
    ('ON_NET_POSITION_SETTLED', 1),
    ('ON_INTERNAL_BOOK_POST', 1);

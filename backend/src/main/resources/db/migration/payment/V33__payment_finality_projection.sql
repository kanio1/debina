-- owner: payment-lifecycle — ADR-N10, EPIC-39 Story 39.2
--
-- Migration impact
-- Schemat/właściciel: payment / sepa_app
-- Tabele: payment.payments
-- Obecna najwyższa migracja: V32
-- Czy migracja była już stosowana: no
-- Przewidywany lock: brief ACCESS EXCLUSIVE for nullable column additions and partial unique index.
-- Ryzyko wielkości tabeli: nullable additions are metadata-only; partial index is justified by the
--   one-authoritative-record-per-payment projection lookup and contains only finalised payments.
-- Backward compatibility: existing payments remain non-final with NULL projection fields.
-- Expand/contract: expand only; no history row is changed.
-- RLS/grants impact: existing payment writer remains the only writer; no settlement or egress grant.
-- Forward-fix: append-only migration.
-- Fresh DB verification: FinalitySchemaMigrationTest.
-- Upgrade verification: FinalityMigrationUpgradePathTest.

ALTER TABLE payment.payments
    ADD COLUMN finality_at timestamptz(3),
    ADD COLUMN finality_record_id uuid;

CREATE UNIQUE INDEX payments_finality_record_id_unique_idx
    ON payment.payments (finality_record_id)
    WHERE finality_record_id IS NOT NULL;

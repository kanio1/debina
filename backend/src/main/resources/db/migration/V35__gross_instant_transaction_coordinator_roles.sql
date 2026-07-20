-- owner: shared security boundary — ADR-N11
--
-- Migration impact
-- Schemat/właściciel: role-only; module function owners are NOLOGIN identities
-- Tabele: none
-- Obecna najwyższa migracja: V34
-- Czy migracja była już stosowana: no
-- Przewidywany lock: catalog-only role creation/grants.
-- Ryzyko wielkości tabeli: none.
-- Backward compatibility: additive, no existing role privileges removed.
-- Expand/contract: expand only.
-- RLS/grants impact: executor receives no table DML; owners receive only own-function needs later.
-- Forward-fix: append-only migration.
-- Fresh DB verification: GrossInstantSecurityMigrationTest.
-- Upgrade verification: GrossInstantSecurityMigrationUpgradePathTest.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'gross_instant_executor_role') THEN
        CREATE ROLE gross_instant_executor_role LOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS
            PASSWORD 'dev-only-gross-instant-executor';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ledger_gross_instant_function_owner') THEN
        CREATE ROLE ledger_gross_instant_function_owner NOLOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'settlement_gross_instant_function_owner') THEN
        CREATE ROLE settlement_gross_instant_function_owner NOLOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'payment_gross_instant_function_owner') THEN
        CREATE ROLE payment_gross_instant_function_owner NOLOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS;
    END IF;
END
$$;

ALTER ROLE gross_instant_executor_role LOGIN PASSWORD 'dev-only-gross-instant-executor';

GRANT CONNECT ON DATABASE sepa_nexus TO gross_instant_executor_role;


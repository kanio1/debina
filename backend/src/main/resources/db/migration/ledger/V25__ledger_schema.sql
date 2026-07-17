-- owner: ledger [MVP] — sepa-nexus-message-flow-and-data-blueprint.md §3.6.2/§4.5/§4.7, EPIC-32
-- Story 32.1. "Crown jewel": append-only, double-entry, NO RLS — protected by table ownership and
-- a dedicated role instead (§4.7's explicit ledger-tables-avoid-RLS decision), mirroring
-- signature's V13 precedent (a genuinely separate module from day one, its own role, never the
-- shared sepa_app connection).

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ledger_role') THEN
        CREATE ROLE ledger_role LOGIN PASSWORD 'dev-only-ledger';
    END IF;
END
$$;

CREATE SCHEMA ledger AUTHORIZATION sepa_migration;

GRANT USAGE ON SCHEMA ledger TO ledger_role;

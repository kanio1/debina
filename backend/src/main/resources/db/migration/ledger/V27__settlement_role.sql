-- owner: ledger [MVP] — sepa-nexus-message-flow-and-data-blueprint.md §3.3/§4.7, EPIC-13 Story 13.3
-- settlement_role is named explicitly by source ("a DB grant test — settlement_role has no write
-- on ledger.*", line 234; ownership doc's forbidden-access list) but does not exist anywhere in
-- this repository yet — com.sepanexus.settlement (EPIC-35, this session) is a pure Java resolver
-- with no schema/DB role of its own so far. This migration creates the role for the first time,
-- with deliberately NO grants anywhere (not even USAGE on any schema) — the entire point of Story
-- 13.3 is proving this role has zero ledger write access, and a role with no grants at all is the
-- correct, minimal starting state, not an oversight to fill in later. Future settlement-owning
-- migrations (if/when settlement gets its own schema) grant this role exactly what it needs then.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'settlement_role') THEN
        CREATE ROLE settlement_role LOGIN PASSWORD 'dev-only-settlement';
    END IF;
END
$$;

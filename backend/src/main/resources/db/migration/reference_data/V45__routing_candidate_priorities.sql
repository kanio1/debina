-- owner: reference-data [MVP] — sepa-nexus-message-flow-and-data-blueprint.md §4.10, EPIC-51 Story 51.1
--
-- Migration impact
-- Schema/owner: reference_data / reference-data.
-- Tables: profile_route_priorities (new, empty catalog table).
-- Previous highest migration: V44 (payment timeout/revocation facts).
-- Applied already: no; additive forward migration only.
-- Expected lock/data-volume risk: catalog-table create only; no existing-table lock or backfill.
-- Compatibility: additive; existing readers and data remain unchanged.
-- RLS/grants: static reference data has ownership grants, not tenant RLS; reference_data_role is
-- the sole writer and routing_role receives read-only access.
-- Fresh/upgrade verification: RouteCandidateCatalogMigrationTest.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'routing_role') THEN
        CREATE ROLE routing_role LOGIN PASSWORD 'dev-only-routing';
    END IF;
END
$$;

CREATE TABLE reference_data.profile_route_priorities (
    scheme text NOT NULL,
    service_level text,
    currency char(3),
    profile_id uuid NOT NULL,
    priority smallint NOT NULL,
    PRIMARY KEY (scheme, service_level, currency, profile_id)
);

REVOKE ALL ON TABLE reference_data.profile_route_priorities FROM PUBLIC;
GRANT USAGE ON SCHEMA reference_data TO routing_role;
GRANT SELECT ON TABLE reference_data.profile_route_priorities TO routing_role;

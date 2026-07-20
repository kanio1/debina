-- owner: reference-data [MVP] — sepa-nexus-message-flow-and-data-blueprint.md §4.10,
-- EPIC-54 Story 54.1, ADR-N12.
--
-- Migration impact
-- Schema/owner: reference_data / reference-data.
-- Tables: profile_fallback_rules (new, initially empty static catalog).
-- Previous highest migration: V48 route-decision evidence.
-- Applied already: no; additive forward migration only.
-- Expected lock/data-volume risk: catalog-table creation only; no existing-table lock or backfill.
-- Compatibility: additive; V48 route decisions remain unchanged until V50 binds the nullable link.
-- RLS/grants: static reference data is not tenant-bearing; reference_data_role is the sole writer,
-- routing_role is read-only, and PUBLIC receives no privilege.
-- Forward-fix: later corrections must be higher migrations.
-- Fresh/upgrade verification: FallbackRuleCatalogMigrationTest and FallbackRuleCatalogMigrationUpgradePathTest.

CREATE TABLE reference_data.profile_fallback_rules (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    profile_id uuid NOT NULL,
    fallback_profile_id uuid NOT NULL,
    priority smallint NOT NULL,
    condition text,
    CONSTRAINT profile_fallback_rules_source_order_unique UNIQUE (profile_id, priority)
);

REVOKE ALL ON TABLE reference_data.profile_fallback_rules FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE ON TABLE reference_data.profile_fallback_rules TO reference_data_role;
GRANT USAGE ON SCHEMA reference_data TO routing_role;
GRANT SELECT ON TABLE reference_data.profile_fallback_rules TO routing_role;

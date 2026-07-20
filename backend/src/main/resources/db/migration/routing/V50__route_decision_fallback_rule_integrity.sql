-- owner: routing [MVP] — sepa-nexus-message-flow-and-data-blueprint.md §4.10,
-- EPIC-54 Story 54.2, ADR-N12.
--
-- Migration impact
-- Schema/owner: routing / routing.
-- Tables: route_decisions (existing V48 immutable decision evidence).
-- Previous highest migration: V49 reference-data fallback catalog.
-- Applied already: no; additive constraints only, no backfill.
-- Expected lock/data-volume risk: brief SHARE ROW EXCLUSIVE while adding constraints; the V48
-- table is new/empty at this wave. CHECK is added NOT VALID then validated as the safe forward
-- pattern; the FK has no pre-existing non-null values to scan.
-- Compatibility: V48 rows with fallback_applied=false and fallback_rule_id NULL remain valid.
-- RLS/grants: routing keeps DML only on routing.*; it receives REFERENCES(id) and existing SELECT
-- on the static catalog, never reference-data DML.
-- Forward-fix: append-only; no rollback migration drops immutable evidence.
-- Fresh/upgrade verification: FallbackDecisionEvidenceMigrationTest and
-- FallbackRuleCatalogMigrationUpgradePathTest.

ALTER TABLE routing.route_decisions
    ADD CONSTRAINT route_decision_fallback_link_matches_flag
    CHECK ((fallback_applied AND fallback_rule_id IS NOT NULL)
        OR (NOT fallback_applied AND fallback_rule_id IS NULL)) NOT VALID;

ALTER TABLE routing.route_decisions
    VALIDATE CONSTRAINT route_decision_fallback_link_matches_flag;

ALTER TABLE routing.route_decisions
    ADD CONSTRAINT route_decision_fallback_rule_fk
    FOREIGN KEY (fallback_rule_id)
    REFERENCES reference_data.profile_fallback_rules (id)
    ON DELETE RESTRICT;

GRANT REFERENCES (id) ON TABLE reference_data.profile_fallback_rules TO routing_role;

-- owner: shared-kernel (ADR-N5) — EPIC-18 Story 18.2
--
-- outbox_dispatcher_role (created V22, ADR-N5 "shared-kernel dispatcher role") was so far scoped
-- only to egress.outbox_events. ADR-N5 itself is [FREEZE] and states the role is granted
-- "explicit SELECT/UPDATE (claim, mark-published) across every module's outbox table" — payment
-- and iso both already own an outbox_events table (payment.outbox_events since V5, iso.outbox_events
-- since V21) that this role has never been granted on. This migration extends the existing role to
-- those two currently-existing outboxes only — never a new table, never a domain-table grant, never
-- INSERT/DELETE/TRUNCATE. Application dispatcher code is deliberately NOT changed here (OutboxDispatcher/
-- IsoOutboxDispatcher continue running under sepa_app, per this session's own scope boundary) — this
-- migration only brings the grant state in line with the frozen ADR-N5 decision so a future dispatcher
-- rewire has the correct role already available and provably narrow.

GRANT USAGE ON SCHEMA payment TO outbox_dispatcher_role;
GRANT SELECT, UPDATE (published_at) ON payment.outbox_events TO outbox_dispatcher_role;

GRANT USAGE ON SCHEMA iso TO outbox_dispatcher_role;
GRANT SELECT, UPDATE (published_at) ON iso.outbox_events TO outbox_dispatcher_role;

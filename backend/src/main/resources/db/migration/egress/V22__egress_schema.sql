-- owner: egress [MVP] — sepa-nexus-message-flow-and-data-blueprint.md §3.6.2/§6, EPIC-43 Story 43.1
-- egress is listed alongside signature as a genuinely separate module from day one (§3.6.3
-- "[DEFER] earliest-needed schemas"), so it gets its own dedicated role here rather than being
-- nested under payment-lifecycle's shared sepa_app connection, mirroring signature's V13 precedent.
--
-- outbox_dispatcher_role (ADR-N5, §4.4): the shared-kernel role that claims/marks-published rows
-- across every module's own <schema>.outbox_events table. It does not exist anywhere in this
-- repository yet — payment.outbox_events (the only prior outbox table) predates this pattern being
-- applied and is still dispatched by sepa_app directly (EPIC-18, retrofitting the remaining
-- modules including payment, is its own separate, not-yet-started epic — not touched here). This
-- migration creates the role for the first time, scoped only to egress.outbox_events (V24); it
-- deliberately does not retrofit payment.outbox_events, which stays out of this story's scope.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'egress_role') THEN
        CREATE ROLE egress_role LOGIN PASSWORD 'dev-only-egress';
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'outbox_dispatcher_role') THEN
        CREATE ROLE outbox_dispatcher_role LOGIN PASSWORD 'dev-only-outbox-dispatcher';
    END IF;
END
$$;

CREATE SCHEMA egress AUTHORIZATION sepa_migration;

GRANT USAGE ON SCHEMA egress TO egress_role;
GRANT USAGE ON SCHEMA egress TO outbox_dispatcher_role;
ALTER DEFAULT PRIVILEGES FOR ROLE sepa_migration IN SCHEMA egress
    GRANT SELECT, INSERT, UPDATE ON TABLES TO egress_role;

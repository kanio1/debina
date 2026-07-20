-- owner: routing [MVP] — sepa-nexus-message-flow-and-data-blueprint.md §4.4/§4.10, ADR-N5, EPIC-52 Story 52.3
-- Migration impact: creates the empty routing-owned schema/runtime reachability table plus the
-- required per-schema outbox/inbox pair. No existing table changes, backfill, or tenant row data.

CREATE SCHEMA routing AUTHORIZATION sepa_migration;

GRANT USAGE ON SCHEMA routing TO routing_role;
GRANT USAGE ON SCHEMA routing TO outbox_dispatcher_role;

CREATE TABLE routing.participant_reachability (
    participant_id uuid NOT NULL,
    profile_id uuid NOT NULL,
    status text NOT NULL CHECK (status IN ('REACHABLE', 'UNAVAILABLE', 'DEGRADED')),
    reachability_type text CHECK (reachability_type IN ('DIRECT', 'INDIRECT', 'REACHABLE_VIA_PARTICIPANT', 'ADDRESSABLE')),
    as_of timestamptz(3) NOT NULL,
    PRIMARY KEY (participant_id, profile_id)
);

REVOKE ALL ON TABLE routing.participant_reachability FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE ON TABLE routing.participant_reachability TO routing_role;

CREATE TABLE routing.outbox_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id uuid NOT NULL,
    topic text NOT NULL,
    type text NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz
) WITH (fillfactor = 90);
CREATE INDEX routing_outbox_todo_idx ON routing.outbox_events (created_at) WHERE published_at IS NULL;
REVOKE ALL ON TABLE routing.outbox_events FROM PUBLIC;
GRANT INSERT, SELECT ON routing.outbox_events TO routing_role;
GRANT SELECT, UPDATE (published_at) ON routing.outbox_events TO outbox_dispatcher_role;

CREATE TABLE routing.inbox_events (
    message_id uuid PRIMARY KEY,
    consumer text NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now()
);
REVOKE ALL ON TABLE routing.inbox_events FROM PUBLIC;
GRANT INSERT, SELECT ON routing.inbox_events TO routing_role;

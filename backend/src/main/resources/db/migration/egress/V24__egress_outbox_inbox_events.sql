-- owner: egress [MVP] — sepa-nexus-message-flow-and-data-blueprint.md §4.4 [PATCH][ADR-N5][FREEZE]
-- egress's own outbox_events/inbox_events pair, required alongside its first migration per ADR-N5
-- ("an outbox_events/inbox_events pair is created alongside each module's first migration, not
-- upfront for all modules"). This is the ADR-N5 internal Kafka-relay mechanism — a distinct table
-- from egress.outbound_messages (V23), which is the external delivery queue. No code in this
-- story writes to these tables yet (nothing here is published to Kafka in Story 43.1's scope); the
-- pair exists so the ownership boundary cannot be violated later, exactly as V13 did for signature
-- before any signing logic existed.

CREATE TABLE egress.outbox_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id uuid NOT NULL,
    topic text NOT NULL,
    type text NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz
) WITH (fillfactor = 90);

CREATE INDEX egress_outbox_todo_idx
    ON egress.outbox_events (created_at)
    WHERE published_at IS NULL;

REVOKE ALL ON egress.outbox_events FROM PUBLIC;
GRANT INSERT, SELECT ON egress.outbox_events TO egress_role;
GRANT SELECT, UPDATE (published_at) ON egress.outbox_events TO outbox_dispatcher_role;

CREATE TABLE egress.inbox_events (
    message_id uuid PRIMARY KEY,
    consumer text NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now()
);

REVOKE ALL ON egress.inbox_events FROM PUBLIC;
GRANT INSERT, SELECT ON egress.inbox_events TO egress_role;

-- owner: iso-adapter [MVP] — sepa-nexus-message-flow-and-data-blueprint.md §4.3c (line 656-660),
-- EPIC-28 Story 28.1. Evidence for XML hardening/parser failures (XXE, entity expansion,
-- disallowed DOCTYPE, malformed XML) that occur BEFORE any iso.iso_messages row can exist — the
-- parser has not reliably determined a message type at that point, so there is no iso_message_id
-- to attach to (source DDL declares none, only raw_message_id). No tenant_id/branch_id columns
-- either — source DDL has none, and every other iso.* table today relies on ownership grants only
-- (no RLS), not per-row tenant scoping; this table follows the same model. Append-only, same
-- pattern as V14 (signature evidence): sepa_app may INSERT/SELECT, never UPDATE/DELETE.

CREATE TABLE iso.iso_message_parse_errors (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    raw_message_id uuid NOT NULL REFERENCES ingress.raw_inbound_messages (id),
    message_type_guess text,
    error_code text NOT NULL,
    error_path text,
    error_message text NOT NULL,
    created_at timestamptz(3) NOT NULL DEFAULT now()
);

CREATE INDEX iso_message_parse_errors_raw_message_idx ON iso.iso_message_parse_errors (raw_message_id);

REVOKE UPDATE, DELETE ON iso.iso_message_parse_errors FROM sepa_app;

-- owner: reference-data [MVP] — sepa-nexus-message-flow-and-data-blueprint.md §4.10, EPIC-52 Stories 52.1–52.2
-- Additive static routing facts; no existing data/table rewrite, backfill, RLS, or inferred
-- eligibility semantics. The generic rule table deliberately stores only source-defined fields.

CREATE TABLE reference_data.participant_capabilities (
    participant_id uuid NOT NULL,
    profile_id uuid NOT NULL,
    access_mode text NOT NULL CHECK (access_mode IN ('DIRECT', 'INDIRECT', 'INTERNAL', 'ADDRESSABLE', 'SERVICED')),
    PRIMARY KEY (participant_id, profile_id)
);

CREATE TABLE reference_data.participant_eligibility_rules (
    profile_id uuid NOT NULL,
    rule_code text NOT NULL,
    rule_value text,
    PRIMARY KEY (profile_id, rule_code)
);

REVOKE ALL ON TABLE reference_data.participant_capabilities, reference_data.participant_eligibility_rules FROM PUBLIC;
GRANT SELECT ON TABLE reference_data.participant_capabilities, reference_data.participant_eligibility_rules TO routing_role;

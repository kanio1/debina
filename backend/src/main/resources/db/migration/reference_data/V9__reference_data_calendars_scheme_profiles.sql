-- owner: reference-data [MVP]/[P1] — sepa-nexus-message-flow-and-data-blueprint.md §4.13 DDL sketch

CREATE TABLE reference_data.scheme_profiles (         -- [MVP] EPC/KIR/STET/TIPS/STEP2-like
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    profile_code text NOT NULL,                        -- e.g. TIPS_LIKE, STET_LIKE
    family text NOT NULL,                               -- GROSS_INSTANT | NET_DEFERRED
    service_level text,
    sla_seconds int,
    netting_mode text,
    valid_from date NOT NULL,
    valid_to date
);

CREATE TABLE reference_data.business_calendars (      -- [P1] TARGET2 (EUR) + Polish (PLN) holidays; cut-off sessions
    calendar_code text NOT NULL,
    business_date date NOT NULL,
    is_business_day boolean NOT NULL,
    session_no smallint,
    cutoff_at timestamptz(3),
    PRIMARY KEY (calendar_code, business_date, session_no)
);

CREATE TABLE reference_data.settlement_cutoff_calendar (  -- [P1] owned here (not settlement) — one source of cut-offs
    profile_id uuid NOT NULL,
    business_date date NOT NULL,
    session_no smallint NOT NULL,
    cutoff_at timestamptz(3) NOT NULL,
    PRIMARY KEY (profile_id, business_date, session_no)
);

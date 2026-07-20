-- owner: routing [MVP] — sepa-nexus-message-flow-and-data-blueprint.md §3.9/§4.10, EPIC-53 Stories 53.1–53.3
-- Additive immutable decision evidence. Tenant-bearing decisions and their dependent evidence
-- are RLS-scoped through app.tenant_id; no routing mutation acts on payment, settlement, or ledger.

CREATE TABLE routing.route_decisions (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    payment_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    selected_profile_id uuid,
    outcome text NOT NULL CHECK (outcome IN (
        'ROUTE_SELECTED', 'FALLBACK_SELECTED', 'PARTICIPANT_UNREACHABLE', 'PROFILE_NOT_ELIGIBLE',
        'AMOUNT_LIMIT_EXCEEDED', 'CURRENCY_NOT_SUPPORTED', 'CUTOFF_REACHED', 'CYCLE_CLOSED',
        'LIQUIDITY_MODE_UNAVAILABLE', 'ROUTE_FAILED', 'ROUTE_PENDING_INVESTIGATION')),
    settlement_basis text CHECK (settlement_basis IN ('GROSS_INSTANT', 'NET_DEFERRED', 'INTERNAL_BOOK')),
    scope text CHECK (scope IN ('INTRA_BRANCH', 'INTER_BRANCH', 'INTERBANK')),
    fallback_applied boolean NOT NULL DEFAULT false,
    fallback_rule_id uuid,
    decision_reason_code text,
    decided_at timestamptz(3) NOT NULL
);
CREATE INDEX rd_payment ON routing.route_decisions (payment_id);

ALTER TABLE routing.route_decisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE routing.route_decisions FORCE ROW LEVEL SECURITY;
CREATE POLICY route_decision_tenant_isolation ON routing.route_decisions
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

CREATE TABLE routing.route_candidate_results (
    decision_id uuid NOT NULL REFERENCES routing.route_decisions(id),
    profile_id uuid NOT NULL,
    seq smallint NOT NULL,
    passed boolean NOT NULL,
    reject_reason text,
    PRIMARY KEY (decision_id, profile_id)
);

CREATE TABLE routing.route_decision_explanations (
    decision_id uuid PRIMARY KEY REFERENCES routing.route_decisions(id),
    explanation jsonb NOT NULL,
    reference_data_version text NOT NULL,
    sim_scenario_id uuid,
    created_at timestamptz(3) NOT NULL
);

ALTER TABLE routing.route_candidate_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE routing.route_candidate_results FORCE ROW LEVEL SECURITY;
CREATE POLICY route_candidate_result_tenant_isolation ON routing.route_candidate_results
    USING (EXISTS (SELECT 1 FROM routing.route_decisions d
                  WHERE d.id = route_candidate_results.decision_id
                    AND d.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid))
    WITH CHECK (EXISTS (SELECT 1 FROM routing.route_decisions d
                        WHERE d.id = route_candidate_results.decision_id
                          AND d.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid));

ALTER TABLE routing.route_decision_explanations ENABLE ROW LEVEL SECURITY;
ALTER TABLE routing.route_decision_explanations FORCE ROW LEVEL SECURITY;
CREATE POLICY route_decision_explanation_tenant_isolation ON routing.route_decision_explanations
    USING (EXISTS (SELECT 1 FROM routing.route_decisions d
                  WHERE d.id = route_decision_explanations.decision_id
                    AND d.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid))
    WITH CHECK (EXISTS (SELECT 1 FROM routing.route_decisions d
                        WHERE d.id = route_decision_explanations.decision_id
                          AND d.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid));

REVOKE ALL ON TABLE routing.route_decisions, routing.route_candidate_results, routing.route_decision_explanations FROM PUBLIC;
GRANT SELECT, INSERT ON TABLE routing.route_decisions, routing.route_candidate_results, routing.route_decision_explanations TO routing_role;

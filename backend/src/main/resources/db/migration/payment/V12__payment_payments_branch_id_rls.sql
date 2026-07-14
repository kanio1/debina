-- owner: payment-lifecycle [MVP] — EPIC-22 Story 22.1, sepa-nexus-message-flow-and-data-blueprint.md §4.7
-- Two-level RLS: tenant_id is mandatory, branch_id is an additional filter only when the session
-- actually sets app.branch_id — an unset/empty branch GUC means "no branch restriction" (matches
-- the same empty-GUC-means-unset convention as V7's tenant_id fix), not "see nothing".

ALTER TABLE payment.payments ADD COLUMN branch_id uuid;

DROP POLICY tenant_isolation ON payment.payments;

CREATE POLICY tenant_isolation ON payment.payments
    USING (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
        AND (
            NULLIF(current_setting('app.branch_id', true), '') IS NULL
            OR branch_id = NULLIF(current_setting('app.branch_id', true), '')::uuid
        )
    );

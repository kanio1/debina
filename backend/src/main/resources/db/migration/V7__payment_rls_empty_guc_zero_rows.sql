DROP POLICY tenant_isolation ON payment.payments;

CREATE POLICY tenant_isolation ON payment.payments
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

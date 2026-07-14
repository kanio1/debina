ALTER TABLE payment.payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment.payments FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payment.payments
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

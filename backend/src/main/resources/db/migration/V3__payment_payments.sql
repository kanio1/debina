CREATE TABLE payment.payments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    end_to_end_id text NOT NULL,
    amount numeric(18, 2) NOT NULL,
    currency char(3) NOT NULL DEFAULT 'EUR',
    debtor_iban text NOT NULL,
    creditor_iban text NOT NULL,
    status text NOT NULL DEFAULT 'RECEIVED'
        CHECK (status IN ('RECEIVED', 'VALIDATED', 'REJECTED', 'DISPATCHED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX payments_tenant_e2e_idx
    ON payment.payments (tenant_id, end_to_end_id);

-- EPIC-76 Story 76.1 / source: message-flow blueprint §2.2b [FREEZE], §3.6.2, §4.7.
-- Migration impact
-- - Schema/owner: payment / payment-lifecycle (sepa_app runtime writer)
-- - Tables: payment.payment_approvals; alters payment.payments.status only to represent pre-FSM rows
-- - Current highest migration: V53; append-only forward migration
-- - Lock: brief ACCESS EXCLUSIVE metadata lock for DROP NOT NULL; no default/backfill/table rewrite
-- - Backward compatibility: existing rows retain non-null RECEIVED; new no-approval path retains its default
-- - RLS/grants: approval isolation joins through RLS-protected payment; PUBLIC explicitly revoked
-- - Forward-fix: corrections require a later migration, never edits to V54
-- - Fresh/upgrade verification: ApprovalPersistenceMigrationTest

ALTER TABLE payment.payments ALTER COLUMN status DROP NOT NULL;

CREATE TABLE payment.payment_approvals (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    payment_id uuid NOT NULL REFERENCES payment.payments(id),
    batch_id uuid,
    status text NOT NULL CHECK (status IN (
        'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED', 'NOT_REQUIRED')),
    maker_user_id text NOT NULL,
    checker_user_id text,
    decision_comment text,
    matrix_rule_id uuid REFERENCES reference_data.approval_matrix_rules(id),
    submitted_for_approval_at timestamptz,
    decided_at timestamptz,
    expires_at timestamptz,
    CHECK (checker_user_id IS NULL OR checker_user_id <> maker_user_id),
    CHECK (status <> 'REJECTED' OR NULLIF(btrim(decision_comment), '') IS NOT NULL),
    CHECK (status <> 'PENDING_APPROVAL'
        OR (checker_user_id IS NULL AND decided_at IS NULL
            AND submitted_for_approval_at IS NOT NULL AND expires_at IS NOT NULL)),
    CHECK (status NOT IN ('APPROVED', 'REJECTED')
        OR (checker_user_id IS NOT NULL AND decided_at IS NOT NULL)),
    CHECK (status <> 'EXPIRED' OR (checker_user_id IS NULL AND decided_at IS NULL)),
    CHECK (status <> 'NOT_REQUIRED'
        OR (checker_user_id IS NULL AND decision_comment IS NULL AND decided_at IS NULL
            AND submitted_for_approval_at IS NULL AND expires_at IS NULL)),
    UNIQUE (payment_id)
);

CREATE INDEX approval_queue_idx
    ON payment.payment_approvals (status, matrix_rule_id)
    WHERE status = 'PENDING_APPROVAL';

CREATE OR REPLACE FUNCTION payment.prevent_approval_matrix_reference_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.matrix_rule_id IS DISTINCT FROM NEW.matrix_rule_id THEN
        RAISE EXCEPTION 'approval matrix rule reference is immutable after submission'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER payment_approvals_matrix_rule_immutable
    BEFORE UPDATE ON payment.payment_approvals
    FOR EACH ROW EXECUTE FUNCTION payment.prevent_approval_matrix_reference_mutation();

ALTER TABLE payment.payment_approvals ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment.payment_approvals FORCE ROW LEVEL SECURITY;

CREATE POLICY payment_approvals_visible_through_payment
    ON payment.payment_approvals
    USING (EXISTS (SELECT 1 FROM payment.payments WHERE payments.id = payment_id))
    WITH CHECK (EXISTS (SELECT 1 FROM payment.payments WHERE payments.id = payment_id));

REVOKE ALL ON TABLE payment.payment_approvals FROM PUBLIC;
REVOKE ALL ON FUNCTION payment.prevent_approval_matrix_reference_mutation() FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE ON TABLE payment.payment_approvals TO sepa_app;

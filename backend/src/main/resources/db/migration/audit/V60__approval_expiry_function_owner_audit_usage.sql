-- Forward fix for V59: the payment-owned expiry command needs schema usage solely to invoke the
-- already-granted typed audit function; it receives no audit table privilege.
GRANT USAGE ON SCHEMA audit TO payment_approval_expiry_function_owner;

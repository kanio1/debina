-- SELECT ... FOR UPDATE in both ADR-N11 settlement command functions requires UPDATE privilege
-- even though the functions never issue an UPDATE against the append-only attempt rows.
GRANT UPDATE ON settlement.settlement_attempts TO settlement_gross_instant_function_owner;

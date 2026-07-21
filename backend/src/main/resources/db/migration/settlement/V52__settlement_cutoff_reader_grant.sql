-- owner: settlement/reference_data read boundary — blueprint §3.9/§4.10/§4.13, EPIC-55 Story 55.1
-- Additive grant only: V9 owns the static calendar, V51 owns runtime cycles. No RLS, writer or routing grant changes.
GRANT SELECT ON reference_data.settlement_cutoff_calendar TO settlement_role;
REVOKE ALL ON reference_data.settlement_cutoff_calendar FROM PUBLIC;

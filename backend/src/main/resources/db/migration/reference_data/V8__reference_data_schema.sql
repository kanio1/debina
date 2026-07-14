DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'reference_data_role') THEN
        CREATE ROLE reference_data_role LOGIN PASSWORD 'dev-only-reference-data';
    END IF;
END
$$;

CREATE SCHEMA reference_data AUTHORIZATION sepa_migration;

GRANT USAGE ON SCHEMA reference_data TO reference_data_role;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA reference_data TO reference_data_role;
ALTER DEFAULT PRIVILEGES FOR ROLE sepa_migration IN SCHEMA reference_data
    GRANT SELECT, INSERT, UPDATE ON TABLES TO reference_data_role;

-- Every other module reads reference-data catalogs; none of them may write here.
GRANT USAGE ON SCHEMA reference_data TO sepa_app;
GRANT SELECT ON ALL TABLES IN SCHEMA reference_data TO sepa_app;
ALTER DEFAULT PRIVILEGES FOR ROLE sepa_migration IN SCHEMA reference_data
    GRANT SELECT ON TABLES TO sepa_app;

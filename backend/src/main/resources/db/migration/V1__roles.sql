DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sepa_migration') THEN
        CREATE ROLE sepa_migration LOGIN PASSWORD 'dev-only-migration';
    END IF;
END
$$;

ALTER ROLE sepa_migration LOGIN PASSWORD 'dev-only-migration';

CREATE ROLE sepa_app
    LOGIN
    PASSWORD 'dev-only-app'
    NOSUPERUSER
    NOBYPASSRLS;

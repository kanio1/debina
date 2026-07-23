package com.sepanexus.epic10;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * EPIC-10 transaction-coordination decision gate — the classic {@code SECURITY DEFINER} search_path
 * hijack (the same class of bug as CVE-2007-2138): a {@code SECURITY DEFINER} function that calls an
 * unqualified helper name resolves it via whatever {@code search_path} is active when it runs. If
 * that path includes a schema an attacker can write to (here: {@code public}, modelling any schema
 * the caller role happens to have {@code CREATE} on) BEFORE the function's own trusted schema, an
 * attacker can plant a same-named function there and have it silently execute with the DEFINER's
 * privileges.
 *
 * <p>Two function variants are compared: {@code safe_stamp_writer} sets an explicit
 * {@code SET search_path = proof_iso, pg_temp} (the documented-safe pattern — the attacker-writable
 * schema is never searched); {@code vulnerable_stamp_writer} has no such clause, so it inherits
 * whatever the CALLING session's {@code search_path} is — and Postgres's own default caller
 * search_path, {@code "$user", public}, puts {@code public} first. This is deliberately reproduced
 * with {@code LANGUAGE plpgsql} (not {@code LANGUAGE sql}) because PL/pgSQL resolves unqualified
 * names against the ACTIVE search_path on every call, which makes the hijack unambiguous to
 * demonstrate — a {@code LANGUAGE sql} function body can be inlined by the planner in ways that
 * complicate reasoning about which search_path applies (the one exception in this proof suite to the
 * project's default "prefer LANGUAGE SQL" guidance, because PL/pgSQL is genuinely needed here).
 */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class SecurityDefinerSearchPathProofTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");

    @BeforeAll
    static void createProofSchemasRolesAndFunctions() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE proof_payment_role LOGIN NOINHERIT PASSWORD 'proof'");
            statement.execute("CREATE ROLE proof_iso_role NOLOGIN");
            // Models an attacker who can write to a schema that happens to be earlier in the
            // default search_path than the trusted one — explicitly granted here because Postgres
            // 15+ no longer grants CREATE on `public` to PUBLIC by default; this proof grants it
            // deliberately to reproduce the classic pre-15 vulnerable default, not to claim
            // `public` is writable in this repo's real database.
            statement.execute("CREATE ROLE proof_untrusted_role LOGIN NOINHERIT PASSWORD 'proof'");
            statement.execute("GRANT CREATE ON SCHEMA public TO proof_untrusted_role");
            statement.execute("GRANT USAGE ON SCHEMA public TO proof_payment_role");

            statement.execute("CREATE SCHEMA proof_iso");
            statement.execute("""
                    CREATE TABLE proof_iso.lineage_tbl (id uuid PRIMARY KEY, payment_id uuid NOT NULL, stamp text NOT NULL)
                    """);
            statement.execute("GRANT USAGE ON SCHEMA proof_iso TO proof_iso_role");
            statement.execute("GRANT SELECT, INSERT ON proof_iso.lineage_tbl TO proof_iso_role");
            statement.execute("GRANT USAGE ON SCHEMA proof_iso TO proof_payment_role");

            // The trusted helper: what a legitimate, un-hijacked call should resolve to.
            statement.execute("CREATE FUNCTION proof_iso.stamp() RETURNS text LANGUAGE sql AS $$ SELECT 'trusted' $$");
            statement.execute("ALTER FUNCTION proof_iso.stamp() OWNER TO proof_iso_role");

            statement.execute("""
                    CREATE FUNCTION proof_iso.safe_stamp_writer(p_payment_id uuid)
                    RETURNS uuid
                    LANGUAGE plpgsql
                    SECURITY DEFINER
                    SET search_path = proof_iso, pg_temp
                    AS $$
                    DECLARE
                        v_id uuid;
                    BEGIN
                        INSERT INTO lineage_tbl (id, payment_id, stamp)
                        VALUES (gen_random_uuid(), p_payment_id, stamp())
                        RETURNING id INTO v_id;
                        RETURN v_id;
                    END;
                    $$
                    """);
            statement.execute("ALTER FUNCTION proof_iso.safe_stamp_writer(uuid) OWNER TO proof_iso_role");
            statement.execute("REVOKE ALL ON FUNCTION proof_iso.safe_stamp_writer(uuid) FROM PUBLIC");
            statement.execute("GRANT EXECUTE ON FUNCTION proof_iso.safe_stamp_writer(uuid) TO proof_payment_role");

            // The deliberately vulnerable twin: no SET search_path clause at all, so it runs with
            // whatever search_path the CALLING session has active.
            statement.execute("""
                    CREATE FUNCTION proof_iso.vulnerable_stamp_writer(p_payment_id uuid)
                    RETURNS uuid
                    LANGUAGE plpgsql
                    SECURITY DEFINER
                    AS $$
                    DECLARE
                        v_id uuid;
                    BEGIN
                        INSERT INTO proof_iso.lineage_tbl (id, payment_id, stamp)
                        VALUES (gen_random_uuid(), p_payment_id, stamp())
                        RETURNING id INTO v_id;
                        RETURN v_id;
                    END;
                    $$
                    """);
            statement.execute("ALTER FUNCTION proof_iso.vulnerable_stamp_writer(uuid) OWNER TO proof_iso_role");
            statement.execute("REVOKE ALL ON FUNCTION proof_iso.vulnerable_stamp_writer(uuid) FROM PUBLIC");
            statement.execute("GRANT EXECUTE ON FUNCTION proof_iso.vulnerable_stamp_writer(uuid) TO proof_payment_role");
        }
    }

    @AfterEach
    void dropAttackerObject() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP FUNCTION IF EXISTS public.stamp()");
        }
    }

    @Test
    void safeSearchPathIgnoresAttackerPlantedFunctionInPublicSchema() throws Exception {
        plantAttackerStampFunction();

        UUID paymentId = UUID.randomUUID();
        try (Connection connection = paymentCallerConnection()) {
            connection.setAutoCommit(false);
            // Caller's own session search_path is the Postgres default, "$user", public — public
            // (where the attacker planted their function) comes before anything else. The function
            // must still resolve to the trusted proof_iso.stamp(), because it explicitly sets its
            // own search_path.
            UUID lineageId = callFunction(connection, "safe_stamp_writer", paymentId);
            connection.commit();

            assertThat(stampFor(lineageId)).isEqualTo("trusted");
        }
    }

    @Test
    void missingSearchPathClauseLetsAttackerPlantedFunctionHijackTheCall() throws Exception {
        plantAttackerStampFunction();

        UUID paymentId = UUID.randomUUID();
        try (Connection connection = paymentCallerConnection()) {
            connection.setAutoCommit(false);
            UUID lineageId = callFunction(connection, "vulnerable_stamp_writer", paymentId);
            connection.commit();

            // This assertion is the proof of the vulnerability, not a bug in the test: the
            // attacker's public.stamp() ran with proof_iso_role's privileges because the function
            // never pinned its own search_path.
            assertThat(stampFor(lineageId)).isEqualTo("HIJACKED");
        }
    }

    private void plantAttackerStampFunction() throws Exception {
        try (Connection connection = untrustedConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE FUNCTION public.stamp() RETURNS text LANGUAGE sql AS $$ SELECT 'HIJACKED' $$");
            }
            connection.commit();
        }
    }

    private static UUID callFunction(Connection connection, String functionName, UUID paymentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT proof_iso." + functionName + "(?)")) {
            statement.setObject(1, paymentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return (UUID) resultSet.getObject(1);
            }
        }
    }

    private static String stampFor(UUID lineageId) throws SQLException {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT stamp FROM proof_iso.lineage_tbl WHERE id = ?")) {
            statement.setObject(1, lineageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }

    private static Connection paymentCallerConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "proof_payment_role", "proof");
    }

    private static Connection untrustedConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "proof_untrusted_role", "proof");
    }
}

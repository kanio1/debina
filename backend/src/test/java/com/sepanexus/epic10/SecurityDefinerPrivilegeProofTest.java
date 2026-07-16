package com.sepanexus.epic10;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * EPIC-10 transaction-coordination decision gate, `SECURITY DEFINER` variant — companion to
 * {@link SetLocalRoleSqlProofTest}. Proves the privilege surface of a narrow, module-owned
 * {@code SECURITY DEFINER} command function: the caller (modelling {@code payment-lifecycle}) can
 * never write the iso-equivalent table directly, only {@code EXECUTE} the one function; the function
 * owner (modelling {@code iso_adapter_role}) is the only direct writer; an unauthorized/untrusted
 * role and {@code PUBLIC} can do neither. Synthetic schemas/roles only (`proof_payment`/`proof_iso`),
 * never wired into production code — kept as permanent documentation for this decision, matching the
 * convention already established by {@code SetLocalRoleSqlProofTest}.
 */
@Testcontainers
class SecurityDefinerPrivilegeProofTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");

    @BeforeAll
    static void createProofSchemasRolesAndFunction() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE proof_payment_role LOGIN NOINHERIT PASSWORD 'proof'");
            statement.execute("CREATE ROLE proof_iso_role NOLOGIN");
            statement.execute("CREATE ROLE proof_untrusted_role LOGIN NOINHERIT PASSWORD 'proof'");

            statement.execute("CREATE SCHEMA proof_payment");
            statement.execute("GRANT USAGE ON SCHEMA proof_payment TO proof_payment_role");

            statement.execute("CREATE SCHEMA proof_iso");
            statement.execute("CREATE TABLE proof_iso.lineage_tbl (id uuid PRIMARY KEY, payment_id uuid NOT NULL, val text NOT NULL)");
            statement.execute("GRANT USAGE ON SCHEMA proof_iso TO proof_iso_role");
            statement.execute("GRANT SELECT, INSERT ON proof_iso.lineage_tbl TO proof_iso_role");
            // proof_payment_role gets USAGE on the schema (required just to resolve the qualified
            // function name — a real, non-obvious Postgres detail found while building this proof:
            // EXECUTE on the function alone is not sufficient) but deliberately NO grant on the
            // table itself — the only door into proof_iso.lineage_tbl is the narrow function below.
            statement.execute("GRANT USAGE ON SCHEMA proof_iso TO proof_payment_role");

            statement.execute("""
                    CREATE FUNCTION proof_iso.record_original_instruction(p_payment_id uuid, p_val text)
                    RETURNS uuid
                    LANGUAGE sql
                    SECURITY DEFINER
                    SET search_path = proof_iso, pg_temp
                    BEGIN ATOMIC
                        INSERT INTO proof_iso.lineage_tbl (id, payment_id, val)
                        VALUES (gen_random_uuid(), p_payment_id, p_val)
                        RETURNING id;
                    END
                    """);
            statement.execute("ALTER FUNCTION proof_iso.record_original_instruction(uuid, text) OWNER TO proof_iso_role");
            statement.execute("REVOKE ALL ON FUNCTION proof_iso.record_original_instruction(uuid, text) FROM PUBLIC");
            statement.execute("GRANT EXECUTE ON FUNCTION proof_iso.record_original_instruction(uuid, text) TO proof_payment_role");
        }
    }

    @Test
    void callerCannotDirectlyWriteIsoTable() throws Exception {
        try (Connection connection = paymentCallerConnection()) {
            connection.setAutoCommit(false);
            SQLException exception = org.junit.jupiter.api.Assertions.assertThrows(SQLException.class, () -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO proof_iso.lineage_tbl (id, payment_id, val) "
                            + "VALUES (gen_random_uuid(), gen_random_uuid(), 'direct-write-attempt')");
                }
            });
            assertThat(exception.getSQLState()).isEqualTo("42501");
            connection.rollback();
        }
    }

    @Test
    void isoOwnerCanDirectlyWriteItsOwnTable() throws Exception {
        try (Connection connection = isoOwnerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int updated = statement.executeUpdate("INSERT INTO proof_iso.lineage_tbl (id, payment_id, val) "
                        + "VALUES (gen_random_uuid(), gen_random_uuid(), 'owner-write')");
                assertThat(updated).isEqualTo(1);
            }
            connection.rollback();
        }
    }

    @Test
    void authorizedCallerCanExecuteTheFunction() throws Exception {
        UUID paymentId = UUID.randomUUID();
        try (Connection connection = paymentCallerConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    "SELECT proof_iso.record_original_instruction(?, ?)")) {
                statement.setObject(1, paymentId);
                statement.setString(2, "authorized-call");
                try (var resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getObject(1)).isNotNull();
                }
            }
            connection.commit();
        }
        assertThat(lineageRowCount(paymentId)).isEqualTo(1);
    }

    @Test
    void unauthorizedCallerCannotExecuteTheFunction() throws Exception {
        try (Connection connection = untrustedConnection()) {
            connection.setAutoCommit(false);
            SQLException exception = org.junit.jupiter.api.Assertions.assertThrows(SQLException.class, () -> {
                try (var statement = connection.prepareStatement("SELECT proof_iso.record_original_instruction(?, ?)")) {
                    statement.setObject(1, UUID.randomUUID());
                    statement.setString(2, "unauthorized-call");
                    statement.execute();
                }
            });
            assertThat(exception.getSQLState()).isEqualTo("42501");
            connection.rollback();
        }
    }

    @Test
    void publicHasNoImplicitExecuteGrant() throws Exception {
        try (Connection connection = adminConnection();
                var statement = connection.prepareStatement("""
                        SELECT has_function_privilege('public', 'proof_iso.record_original_instruction(uuid, text)', 'EXECUTE')
                        """);
                var resultSet = statement.executeQuery()) {
            resultSet.next();
            assertThat(resultSet.getBoolean(1)).isFalse();
        }
    }

    private static int lineageRowCount(UUID paymentId) throws Exception {
        try (Connection connection = adminConnection();
                var statement = connection.prepareStatement("SELECT count(*) FROM proof_iso.lineage_tbl WHERE payment_id = ?")) {
            statement.setObject(1, paymentId);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }

    private static Connection paymentCallerConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "proof_payment_role", "proof");
    }

    private static Connection isoOwnerConnection() throws SQLException {
        // proof_iso_role is NOLOGIN by design (only ever runs as the function's definer, never as a
        // direct session principal) — test_admin impersonates it here purely to prove the grant
        // shape ("the owner CAN write its own table"), the same way SetLocalRoleSqlProofTest proves
        // negative grants via role membership rather than a literal LOGIN connection.
        Connection connection = adminConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE proof_iso_role");
        }
        return connection;
    }

    private static Connection untrustedConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "proof_untrusted_role", "proof");
    }
}

package com.sepanexus.epic10;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * EPIC-10 transaction-coordination decision gate — proves a {@code SECURITY DEFINER} function never
 * leaks role, {@code search_path}, or tenant/branch GUC state onto the caller's connection, whether
 * the surrounding transaction commits, rolls back after a caller failure, or rolls back after the
 * function itself fails — a pooled connection returned in any of these three cases must come back
 * "clean" for the next borrower. Also proves two parallel connections (modelling two concurrent
 * tenants) never see each other's GUC or role state.
 */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class SecurityDefinerPoolIsolationProofTest {

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

            statement.execute("CREATE SCHEMA proof_iso");
            statement.execute("CREATE TABLE proof_iso.lineage_tbl (id uuid PRIMARY KEY, payment_id uuid NOT NULL, val text NOT NULL)");
            statement.execute("GRANT USAGE ON SCHEMA proof_iso TO proof_iso_role");
            statement.execute("GRANT SELECT, INSERT ON proof_iso.lineage_tbl TO proof_iso_role");
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

            statement.execute("""
                    CREATE FUNCTION proof_iso.record_original_instruction_failing(p_payment_id uuid)
                    RETURNS uuid
                    LANGUAGE sql
                    SECURITY DEFINER
                    SET search_path = proof_iso, pg_temp
                    BEGIN ATOMIC
                        INSERT INTO proof_iso.lineage_tbl (id, payment_id, val)
                        VALUES (gen_random_uuid(), p_payment_id, NULL)
                        RETURNING id;
                    END
                    """);
            statement.execute("ALTER FUNCTION proof_iso.record_original_instruction_failing(uuid) OWNER TO proof_iso_role");
            statement.execute("REVOKE ALL ON FUNCTION proof_iso.record_original_instruction_failing(uuid) FROM PUBLIC");
            statement.execute("GRANT EXECUTE ON FUNCTION proof_iso.record_original_instruction_failing(uuid) TO proof_payment_role");
        }
    }

    @Test
    void connectionCleanAfterCommit() throws Exception {
        try (Connection connection = paymentCallerConnection()) {
            connection.setAutoCommit(false);
            setTenantGuc(connection, "tenant-commit");
            callFunction(connection, UUID.randomUUID(), "commit-clean");
            connection.commit();

            // Same physical connection, now past the transaction boundary: set_config(..., true) is
            // transaction-local — it reverts at commit exactly the same way it reverts at rollback
            // (Postgres's own custom-GUC-unset convention returns the empty string here, never SQL
            // NULL, matching the established `NULLIF(current_setting(...), '')` pattern already used
            // by TenantGucConfigurer/the RLS policies elsewhere in this codebase).
            assertThat(currentUser(connection)).isEqualTo("proof_payment_role");
            assertThat(searchPath(connection)).startsWith("\"$user\", public");
            assertThat(tenantGuc(connection)).isEmpty();
        }
    }

    @Test
    void connectionCleanAfterCallerRollback() throws Exception {
        try (Connection connection = paymentCallerConnection()) {
            connection.setAutoCommit(false);
            setTenantGuc(connection, "tenant-rollback");
            callFunction(connection, UUID.randomUUID(), "rollback-clean");
            connection.rollback();

            assertThat(currentUser(connection)).isEqualTo("proof_payment_role");
            assertThat(searchPath(connection)).startsWith("\"$user\", public");
            // set_config(..., true) is transaction-scoped ("true" = local) — after rollback it must
            // revert to unset, exactly like the tenant GUC convention already established for
            // TenantGucConfigurer elsewhere in this codebase.
            assertThat(tenantGuc(connection)).isEmpty();
        }
    }

    @Test
    void connectionCleanAfterFunctionFailureRollback() throws Exception {
        try (Connection connection = paymentCallerConnection()) {
            connection.setAutoCommit(false);
            setTenantGuc(connection, "tenant-fn-failure");
            org.junit.jupiter.api.Assertions.assertThrows(SQLException.class, () -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT proof_iso.record_original_instruction_failing(?)")) {
                    statement.setObject(1, UUID.randomUUID());
                    statement.execute();
                }
            });
            connection.rollback();

            assertThat(currentUser(connection)).isEqualTo("proof_payment_role");
            assertThat(searchPath(connection)).startsWith("\"$user\", public");
            assertThat(tenantGuc(connection)).isEmpty();
        }
    }

    @Test
    void parallelConnectionsDoNotLeakGucOrRoleAcrossEachOther() throws Exception {
        try (Connection connectionA = paymentCallerConnection(); Connection connectionB = paymentCallerConnection()) {
            connectionA.setAutoCommit(false);
            connectionB.setAutoCommit(false);

            setTenantGuc(connectionA, "tenant-a");
            setTenantGuc(connectionB, "tenant-b");

            callFunction(connectionA, UUID.randomUUID(), "parallel-a");
            callFunction(connectionB, UUID.randomUUID(), "parallel-b");

            assertThat(tenantGuc(connectionA)).isEqualTo("tenant-a");
            assertThat(tenantGuc(connectionB)).isEqualTo("tenant-b");
            assertThat(currentUser(connectionA)).isEqualTo("proof_payment_role");
            assertThat(currentUser(connectionB)).isEqualTo("proof_payment_role");

            connectionA.commit();
            connectionB.rollback();
        }
    }

    private static void setTenantGuc(Connection connection, String tenant) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
            statement.setString(1, tenant);
            statement.execute();
        }
    }

    private static String tenantGuc(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT current_setting('app.tenant_id', true)")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private static String searchPath(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SHOW search_path")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private static String currentUser(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT current_user")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private static UUID callFunction(Connection connection, UUID paymentId, String val) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT proof_iso.record_original_instruction(?, ?)")) {
            statement.setObject(1, paymentId);
            statement.setString(2, val);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return (UUID) resultSet.getObject(1);
            }
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }

    private static Connection paymentCallerConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "proof_payment_role", "proof");
    }
}

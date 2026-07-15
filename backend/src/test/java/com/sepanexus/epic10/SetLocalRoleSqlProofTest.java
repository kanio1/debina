package com.sepanexus.epic10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * EPIC-10 transaction-coordination decision gate (§15 of the session packet, {@code [OPEN-QUESTION]}
 * in {@code planning/epics/EPIC-10-iso-lineage-ownership.md}): a lightweight, synthetic model of
 * "one login role, two narrower DML roles for two schema owners" — proving or disproving whether
 * {@code SET LOCAL ROLE} inside ONE Postgres transaction can stand in for today's single-writer
 * ({@code sepa_app}) atomicity once {@code payment-lifecycle} and {@code iso-adapter} get separate
 * DB roles (Story 10.1). Entirely synthetic (schemas {@code proof_payment}/{@code proof_iso}, roles
 * {@code proof_*}) — proves the SQL/transaction mechanism in isolation, deliberately not wired into
 * any production code path. Kept as a permanent, documented proof (not deleted after the session)
 * because it is the direct evidence backing the EPIC-10 decision memo — a future session picking up
 * Story 10.1 needs this proof re-runnable, not just described in prose. See
 * {@link SetLocalRoleJpaFlushProofTest} for the JPA-specific hazard this SQL-only proof cannot show.
 */
@Testcontainers
class SetLocalRoleSqlProofTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");

    @BeforeAll
    static void createProofSchemasAndRoles() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE proof_runtime_login LOGIN NOINHERIT PASSWORD 'proof'");
            statement.execute("CREATE ROLE proof_payment_role NOLOGIN");
            statement.execute("CREATE ROLE proof_iso_role NOLOGIN");
            statement.execute("GRANT proof_payment_role TO proof_runtime_login");
            statement.execute("GRANT proof_iso_role TO proof_runtime_login");

            statement.execute("CREATE SCHEMA proof_payment");
            statement.execute("CREATE TABLE proof_payment.tbl (id uuid PRIMARY KEY, val text)");
            statement.execute("CREATE TABLE proof_payment.history_tbl (id uuid PRIMARY KEY, val text)");
            statement.execute("GRANT USAGE ON SCHEMA proof_payment TO proof_payment_role");
            statement.execute("GRANT SELECT, INSERT ON proof_payment.tbl, proof_payment.history_tbl TO proof_payment_role");

            statement.execute("CREATE SCHEMA proof_iso");
            statement.execute("CREATE TABLE proof_iso.tbl (id uuid PRIMARY KEY, val text)");
            statement.execute("GRANT USAGE ON SCHEMA proof_iso TO proof_iso_role");
            statement.execute("GRANT SELECT, INSERT ON proof_iso.tbl TO proof_iso_role");
        }
    }

    @Test
    void sameTransactionAcrossRoleSwitches_sharesOneTxidAndBothRolesWrite() throws Exception {
        try (Connection connection = runtimeLoginConnection()) {
            connection.setAutoCommit(false);

            setRole(connection, "proof_payment_role");
            long txidAtPaymentWrite = txidCurrent(connection);
            String userAtPaymentWrite = currentUser(connection);
            insertInto(connection, "proof_payment.tbl", UUID.randomUUID());

            setRole(connection, "proof_iso_role");
            long txidAtIsoWrite = txidCurrent(connection);
            String userAtIsoWrite = currentUser(connection);
            insertInto(connection, "proof_iso.tbl", UUID.randomUUID());

            setRole(connection, "proof_payment_role");
            long txidAtHistoryWrite = txidCurrent(connection);
            insertInto(connection, "proof_payment.history_tbl", UUID.randomUUID());

            connection.commit();

            assertThat(txidAtIsoWrite).isEqualTo(txidAtPaymentWrite);
            assertThat(txidAtHistoryWrite).isEqualTo(txidAtPaymentWrite);
            assertThat(userAtPaymentWrite).isEqualTo("proof_payment_role");
            assertThat(userAtIsoWrite).isEqualTo("proof_iso_role");
        }

        assertThat(countRows("proof_payment.tbl")).isEqualTo(1);
        assertThat(countRows("proof_iso.tbl")).isEqualTo(1);
        assertThat(countRows("proof_payment.history_tbl")).isEqualTo(1);
    }

    @Test
    void runtimeLoginWithoutSetRoleCannotWriteEitherSchema() throws Exception {
        try (Connection connection = runtimeLoginConnection()) {
            connection.setAutoCommit(false);
            assertThat(sessionUser(connection)).isEqualTo("proof_runtime_login");
            assertThat(currentUser(connection)).isEqualTo("proof_runtime_login");

            assertInsufficientPrivilege(() -> insertInto(connection, "proof_payment.tbl", UUID.randomUUID()));
            connection.rollback();
            assertInsufficientPrivilege(() -> insertInto(connection, "proof_iso.tbl", UUID.randomUUID()));
            connection.rollback();
        }
    }

    @Test
    void paymentRoleCannotWriteIsoSchemaAndIsoRoleCannotWritePaymentSchema() throws Exception {
        try (Connection connection = runtimeLoginConnection()) {
            connection.setAutoCommit(false);
            setRole(connection, "proof_payment_role");
            assertInsufficientPrivilege(() -> insertInto(connection, "proof_iso.tbl", UUID.randomUUID()));
            connection.rollback();
        }
        try (Connection connection = runtimeLoginConnection()) {
            connection.setAutoCommit(false);
            setRole(connection, "proof_iso_role");
            assertInsufficientPrivilege(() -> insertInto(connection, "proof_payment.tbl", UUID.randomUUID()));
            connection.rollback();
        }
    }

    @Test
    void rollbackAfterErrorUndoesRowsFromBothRolesInTheSameTransaction() throws Exception {
        UUID paymentRowId = UUID.randomUUID();
        UUID isoRowId = UUID.randomUUID();

        try (Connection connection = runtimeLoginConnection()) {
            connection.setAutoCommit(false);
            setRole(connection, "proof_payment_role");
            insertInto(connection, "proof_payment.tbl", paymentRowId);
            setRole(connection, "proof_iso_role");
            insertInto(connection, "proof_iso.tbl", isoRowId);
            // Force a real error: iso role has no privilege on proof_payment.tbl, so writing the
            // "history" row under the wrong role fails exactly like a real cross-schema mistake would.
            assertInsufficientPrivilege(() -> insertInto(connection, "proof_payment.history_tbl", UUID.randomUUID()));
            connection.rollback();
        }

        assertThat(rowExists("proof_payment.tbl", paymentRowId)).isFalse();
        assertThat(rowExists("proof_iso.tbl", isoRowId)).isFalse();
    }

    @Test
    void setLocalRoleAutomaticallyRevertsAtTransactionEnd_commit() throws Exception {
        try (Connection connection = runtimeLoginConnection()) {
            connection.setAutoCommit(false);
            setRole(connection, "proof_payment_role");
            assertThat(currentUser(connection)).isEqualTo("proof_payment_role");
            connection.commit();

            // Same physical connection, new transaction: SET LOCAL from the previous transaction
            // must NOT still be in effect — this is what makes SET LOCAL ROLE pool-safe without an
            // explicit RESET ROLE, unlike plain SET ROLE (session-scoped, persists until changed).
            assertThat(currentUser(connection)).isEqualTo("proof_runtime_login");
        }
    }

    @Test
    void setLocalRoleAutomaticallyRevertsAtTransactionEnd_rollback() throws Exception {
        try (Connection connection = runtimeLoginConnection()) {
            connection.setAutoCommit(false);
            setRole(connection, "proof_iso_role");
            assertThat(currentUser(connection)).isEqualTo("proof_iso_role");
            connection.rollback();

            assertThat(currentUser(connection)).isEqualTo("proof_runtime_login");
        }
    }

    @Test
    void parallelConnectionsDoNotLeakRoleOrTenantGucAcrossEachOther() throws Exception {
        try (Connection connectionA = runtimeLoginConnection(); Connection connectionB = runtimeLoginConnection()) {
            connectionA.setAutoCommit(false);
            connectionB.setAutoCommit(false);

            setRole(connectionA, "proof_payment_role");
            setLocalTenantGuc(connectionA, "tenant-a");
            setRole(connectionB, "proof_iso_role");
            setLocalTenantGuc(connectionB, "tenant-b");

            assertThat(currentUser(connectionA)).isEqualTo("proof_payment_role");
            assertThat(currentUser(connectionB)).isEqualTo("proof_iso_role");
            assertThat(tenantGuc(connectionA)).isEqualTo("tenant-a");
            assertThat(tenantGuc(connectionB)).isEqualTo("tenant-b");

            connectionA.commit();
            connectionB.rollback();
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void assertInsufficientPrivilege(ThrowingRunnable runnable) {
        SQLException exception = (SQLException) org.junit.jupiter.api.Assertions.assertThrows(SQLException.class,
                runnable::run);
        assertThat(exception.getSQLState()).isEqualTo("42501");
    }

    private static void setRole(Connection connection, String role) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET LOCAL ROLE " + role);
        }
    }

    private static void setLocalTenantGuc(Connection connection, String tenant) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, true)")) {
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

    private static void insertInto(Connection connection, String table, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + table + " (id, val) VALUES (?, 'proof')")) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
    }

    private static long txidCurrent(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT txid_current()")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static String currentUser(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT current_user")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private static String sessionUser(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT session_user")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private static int countRows(String table) throws SQLException {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM " + table)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static boolean rowExists(String table, UUID id) throws SQLException {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM " + table + " WHERE id = ?")) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }

    private static Connection runtimeLoginConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "proof_runtime_login", "proof");
    }
}

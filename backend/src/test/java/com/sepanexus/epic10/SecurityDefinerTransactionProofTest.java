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
 * EPIC-10 transaction-coordination decision gate — proves that a caller-invoked
 * {@code SECURITY DEFINER} function participates in the CALLER's physical transaction (same
 * {@code txid}), that a failure anywhere (caller-side after the call, or inside the function itself)
 * rolls back everything atomically, and that {@code current_user} only changes for the duration of
 * the function call itself, never for the caller's session as a whole — the key difference from
 * {@link SetLocalRoleSqlProofTest}, where the caller's own {@code SET LOCAL ROLE} persists until
 * end-of-transaction and can leak into whatever code runs next in the same transaction.
 */
@Testcontainers
class SecurityDefinerTransactionProofTest {

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

            statement.execute("CREATE SCHEMA proof_payment");
            statement.execute("CREATE TABLE proof_payment.history_tbl (id uuid PRIMARY KEY, payment_id uuid NOT NULL, val text NOT NULL)");
            statement.execute("GRANT USAGE ON SCHEMA proof_payment TO proof_payment_role");
            statement.execute("GRANT SELECT, INSERT ON proof_payment.history_tbl TO proof_payment_role");

            statement.execute("CREATE SCHEMA proof_iso");
            statement.execute("""
                    CREATE TABLE proof_iso.lineage_tbl (
                        id uuid PRIMARY KEY,
                        payment_id uuid NOT NULL,
                        val text NOT NULL,
                        recorded_txid bigint NOT NULL,
                        recorded_current_user text NOT NULL
                    )
                    """);
            statement.execute("GRANT USAGE ON SCHEMA proof_iso TO proof_iso_role");
            statement.execute("GRANT SELECT, INSERT ON proof_iso.lineage_tbl TO proof_iso_role");
            statement.execute("GRANT USAGE ON SCHEMA proof_iso TO proof_payment_role");

            // Records its own txid_current() and current_user as it runs — the only way to observe
            // "what was active inside the function" from outside, since the function itself is one
            // opaque statement to the caller.
            statement.execute("""
                    CREATE FUNCTION proof_iso.record_original_instruction(p_payment_id uuid, p_val text)
                    RETURNS uuid
                    LANGUAGE sql
                    SECURITY DEFINER
                    SET search_path = proof_iso, pg_temp
                    BEGIN ATOMIC
                        INSERT INTO proof_iso.lineage_tbl (id, payment_id, val, recorded_txid, recorded_current_user)
                        VALUES (gen_random_uuid(), p_payment_id, p_val, txid_current(), current_user)
                        RETURNING id;
                    END
                    """);
            statement.execute("ALTER FUNCTION proof_iso.record_original_instruction(uuid, text) OWNER TO proof_iso_role");
            statement.execute("REVOKE ALL ON FUNCTION proof_iso.record_original_instruction(uuid, text) FROM PUBLIC");
            statement.execute("GRANT EXECUTE ON FUNCTION proof_iso.record_original_instruction(uuid, text) TO proof_payment_role");

            // A second function, deliberately failing (NOT NULL violation), to prove function-internal
            // failures roll back the caller's writes too.
            statement.execute("""
                    CREATE FUNCTION proof_iso.record_original_instruction_failing(p_payment_id uuid)
                    RETURNS uuid
                    LANGUAGE sql
                    SECURITY DEFINER
                    SET search_path = proof_iso, pg_temp
                    BEGIN ATOMIC
                        INSERT INTO proof_iso.lineage_tbl (id, payment_id, val, recorded_txid, recorded_current_user)
                        VALUES (gen_random_uuid(), p_payment_id, NULL, txid_current(), current_user)
                        RETURNING id;
                    END
                    """);
            statement.execute("ALTER FUNCTION proof_iso.record_original_instruction_failing(uuid) OWNER TO proof_iso_role");
            statement.execute("REVOKE ALL ON FUNCTION proof_iso.record_original_instruction_failing(uuid) FROM PUBLIC");
            statement.execute("GRANT EXECUTE ON FUNCTION proof_iso.record_original_instruction_failing(uuid) TO proof_payment_role");
        }
    }

    @Test
    void functionParticipatesInCallersPhysicalTransaction_sameTxid() throws Exception {
        UUID paymentId = UUID.randomUUID();
        try (Connection connection = paymentCallerConnection()) {
            connection.setAutoCommit(false);
            long callerTxidBefore = txidCurrent(connection);

            UUID lineageId = callFunction(connection, paymentId, "same-tx-check");

            long callerTxidAfter = txidCurrent(connection);
            connection.commit();

            assertThat(callerTxidAfter).isEqualTo(callerTxidBefore);
            long recordedTxid = recordedTxidFor(lineageId);
            assertThat(recordedTxid).isEqualTo(callerTxidBefore);
        }
    }

    @Test
    void sessionUserAndCurrentUserRestoredAfterFunctionReturns() throws Exception {
        UUID paymentId = UUID.randomUUID();
        try (Connection connection = paymentCallerConnection()) {
            connection.setAutoCommit(false);
            assertThat(sessionUser(connection)).isEqualTo("proof_payment_role");
            assertThat(currentUser(connection)).isEqualTo("proof_payment_role");

            UUID lineageId = callFunction(connection, paymentId, "user-check");

            // Immediately after the function call returns, in the SAME transaction — current_user
            // must already be back to the caller, not the function owner.
            assertThat(currentUser(connection)).isEqualTo("proof_payment_role");
            assertThat(sessionUser(connection)).isEqualTo("proof_payment_role");

            connection.commit();

            assertThat(recordedUserFor(lineageId)).isEqualTo("proof_iso_role");
        }
    }

    @Test
    void commitPersistsBothCallerAndFunctionWrites() throws Exception {
        UUID paymentId = UUID.randomUUID();
        try (Connection connection = paymentCallerConnection()) {
            connection.setAutoCommit(false);
            insertHistoryRow(connection, paymentId, "before-function");
            callFunction(connection, paymentId, "commit-check");
            insertHistoryRow(connection, paymentId, "after-function");
            connection.commit();
        }

        assertThat(historyRowCount(paymentId)).isEqualTo(2);
        assertThat(lineageRowCount(paymentId)).isEqualTo(1);
    }

    @Test
    void callerFailureAfterFunctionSuccessRollsBackBothSchemas() throws Exception {
        UUID paymentId = UUID.randomUUID();
        try (Connection connection = paymentCallerConnection()) {
            connection.setAutoCommit(false);
            insertHistoryRow(connection, paymentId, "before-function");
            callFunction(connection, paymentId, "rollback-check");

            // Force a caller-side failure after the function already "succeeded" from the caller's
            // point of view.
            org.junit.jupiter.api.Assertions.assertThrows(SQLException.class,
                    () -> insertHistoryRow(connection, null, "forced-null-violation"));
            connection.rollback();
        }

        assertThat(historyRowCount(paymentId)).isZero();
        assertThat(lineageRowCount(paymentId)).isZero();
    }

    @Test
    void functionInternalFailureRollsBackCallerWritesToo() throws Exception {
        UUID paymentId = UUID.randomUUID();
        try (Connection connection = paymentCallerConnection()) {
            connection.setAutoCommit(false);
            insertHistoryRow(connection, paymentId, "before-failing-function");

            org.junit.jupiter.api.Assertions.assertThrows(SQLException.class, () -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT proof_iso.record_original_instruction_failing(?)")) {
                    statement.setObject(1, paymentId);
                    statement.execute();
                }
            });
            connection.rollback();
        }

        assertThat(historyRowCount(paymentId)).isZero();
        assertThat(lineageRowCount(paymentId)).isZero();
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

    private static void insertHistoryRow(Connection connection, UUID paymentId, String val) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO proof_payment.history_tbl (id, payment_id, val) VALUES (gen_random_uuid(), ?, ?)")) {
            statement.setObject(1, paymentId);
            statement.setString(2, val);
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

    private static long recordedTxidFor(UUID lineageId) throws SQLException {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT recorded_txid FROM proof_iso.lineage_tbl WHERE id = ?")) {
            statement.setObject(1, lineageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static String recordedUserFor(UUID lineageId) throws SQLException {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT recorded_current_user FROM proof_iso.lineage_tbl WHERE id = ?")) {
            statement.setObject(1, lineageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }

    private static int historyRowCount(UUID paymentId) throws SQLException {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM proof_payment.history_tbl WHERE payment_id = ?")) {
            statement.setObject(1, paymentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static int lineageRowCount(UUID paymentId) throws SQLException {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM proof_iso.lineage_tbl WHERE payment_id = ?")) {
            statement.setObject(1, paymentId);
            try (ResultSet resultSet = statement.executeQuery()) {
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
}

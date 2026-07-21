package com.sepanexus.modules.paymentlifecycle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Structural RED proof for EPIC-76 Story 76.1.  The frozen maker–checker prefix gate needs a
 * durable approval axis and a payment row that may exist before the business FSM starts.
 */
class ApprovalPersistenceMigrationTest {

    @Test
    void freshMigrationCreatesTheApprovalAxisAndAllowsPreFsmBusinessStatus() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18")
                .withDatabaseName("sepa_nexus")
                .withUsername("test_admin")
                .withPassword("test_admin")) {
            postgres.start();
            try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), "test_admin", "test_admin");
                    Statement statement = connection.createStatement()) {
                statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
            }
            Flyway.configure().dataSource(postgres.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                    .locations("filesystem:src/main/resources/db/migration").load().migrate();

            try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), "test_admin", "test_admin");
                    Statement statement = connection.createStatement();
                    ResultSet status = statement.executeQuery("""
                            SELECT is_nullable
                            FROM information_schema.columns
                            WHERE table_schema = 'payment' AND table_name = 'payments' AND column_name = 'status'
                            """)) {
                assertThat(status.next()).isTrue();
                assertThat(status.getString("is_nullable")).isEqualTo("YES");
                try (ResultSet tables = statement.executeQuery("""
                        SELECT to_regclass('payment.payment_approvals') AS approval_table,
                               to_regclass('reference_data.approval_matrix_rules') AS matrix_table
                        """)) {
                    assertThat(tables.next()).isTrue();
                    assertThat(tables.getString("approval_table")).isEqualTo("payment.payment_approvals");
                    assertThat(tables.getString("matrix_table")).isEqualTo("reference_data.approval_matrix_rules");
                }
            }
        }
    }

    @Test
    void V52UpgradePreservesExistingPaymentsAndEnforcesApprovalOwnershipAndInvariants() throws Exception {
        try (PostgreSQLContainer<?> postgres = newPostgres()) {
            postgres.start();
            createMigrationRole(postgres);

            Flyway.configure().dataSource(postgres.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                    .locations("filesystem:src/main/resources/db/migration").target("52").load().migrate();

            UUID existingPayment = UUID.randomUUID();
            UUID tenant = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
            UUID branch = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
            try (Connection admin = admin(postgres); PreparedStatement insert = admin.prepareStatement("""
                    INSERT INTO payment.payments (id, tenant_id, branch_id, amount, currency, debtor_iban, creditor_iban)
                    VALUES (?, ?, ?, 10.00, 'EUR', 'DEBTOR', 'CREDITOR')
                    """)) {
                insert.setObject(1, existingPayment);
                insert.setObject(2, tenant);
                insert.setObject(3, branch);
                insert.executeUpdate();
            }

            Flyway.configure().dataSource(postgres.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                    .locations("filesystem:src/main/resources/db/migration").load().migrate();

            try (Connection admin = admin(postgres); Statement statement = admin.createStatement();
                    ResultSet existing = statement.executeQuery("SELECT status FROM payment.payments WHERE id = '"
                            + existingPayment + "'")) {
                assertThat(existing.next()).isTrue();
                assertThat(existing.getString(1)).isEqualTo("RECEIVED");
            }

            UUID ruleId = insertMatrixRule(postgres, tenant);
            UUID pendingPayment = insertPaymentAsApp(postgres, tenant, branch, null);
            UUID approvalId = insertPendingApprovalAsApp(postgres, pendingPayment, ruleId);

            assertThat(queryApprovalCountAsApp(postgres, tenant, branch)).isEqualTo(1);
            assertThat(queryApprovalCountAsApp(postgres, tenant,
                    UUID.fromString("00000000-0000-0000-0000-0000000000b2"))).isZero();
            assertThat(queryApprovalCountWithoutTenant(postgres)).isZero();

            assertThatThrownBy(() -> insertPendingApprovalAsApp(postgres, pendingPayment, ruleId))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> updateMatrixReferenceAsApp(postgres, tenant, branch, approvalId, UUID.randomUUID()))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertRejectedWithBlankCommentAsAdmin(postgres, existingPayment))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertSelfApprovedAsAdmin(postgres, insertPaymentAsAdmin(postgres, tenant, branch)))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertMatrixRuleAsApp(postgres, tenant))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertApprovalAsReferenceDataRole(postgres, existingPayment))
                    .isInstanceOf(SQLException.class);

            try (Connection admin = admin(postgres); Statement statement = admin.createStatement();
                    ResultSet privileges = statement.executeQuery("""
                            SELECT NOT EXISTS (
                                       SELECT FROM pg_class c
                                       CROSS JOIN LATERAL aclexplode(coalesce(c.relacl, acldefault('r', c.relowner))) acl
                                       WHERE c.oid = 'payment.payment_approvals'::regclass
                                         AND acl.grantee = 0 AND acl.privilege_type = 'INSERT'
                                   ) AS public_payment_insert_revoked,
                                   NOT EXISTS (
                                       SELECT FROM pg_class c
                                       CROSS JOIN LATERAL aclexplode(coalesce(c.relacl, acldefault('r', c.relowner))) acl
                                       WHERE c.oid = 'reference_data.approval_matrix_rules'::regclass
                                         AND acl.grantee = 0 AND acl.privilege_type = 'INSERT'
                                   ) AS public_matrix_insert_revoked
                            """)) {
                assertThat(privileges.next()).isTrue();
                assertThat(privileges.getBoolean("public_payment_insert_revoked")).isTrue();
                assertThat(privileges.getBoolean("public_matrix_insert_revoked")).isTrue();
            }
        }
    }

    private static PostgreSQLContainer<?> newPostgres() {
        return new PostgreSQLContainer<>("postgres:18")
                .withDatabaseName("sepa_nexus")
                .withUsername("test_admin")
                .withPassword("test_admin");
    }

    private static void createMigrationRole(PostgreSQLContainer<?> postgres) throws Exception {
        try (Connection connection = admin(postgres); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
    }

    private static Connection admin(PostgreSQLContainer<?> postgres) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), "test_admin", "test_admin");
    }

    private static UUID insertMatrixRule(PostgreSQLContainer<?> postgres, UUID tenant) throws Exception {
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), "reference_data_role",
                "dev-only-reference-data")) {
            setTenant(connection, tenant, null);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO reference_data.approval_matrix_rules (tenant_id, valid_from)
                    VALUES (?, CURRENT_DATE) RETURNING id
                    """)) {
                insert.setObject(1, tenant);
                try (ResultSet result = insert.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    return (UUID) result.getObject(1);
                }
            }
        }
    }

    private static UUID insertPaymentAsApp(PostgreSQLContainer<?> postgres, UUID tenant, UUID branch, String status)
            throws Exception {
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), "sepa_app", "dev-only-app")) {
            setTenant(connection, tenant, branch);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO payment.payments (tenant_id, branch_id, amount, currency, debtor_iban, creditor_iban, status)
                    VALUES (?, ?, 12.00, 'EUR', 'DEBTOR', 'CREDITOR', ?) RETURNING id
                    """)) {
                insert.setObject(1, tenant);
                insert.setObject(2, branch);
                insert.setString(3, status);
                try (ResultSet result = insert.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    return (UUID) result.getObject(1);
                }
            }
        }
    }

    private static UUID insertPendingApprovalAsApp(PostgreSQLContainer<?> postgres, UUID paymentId, UUID ruleId)
            throws Exception {
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), "sepa_app", "dev-only-app")) {
            // The payment's tenant/branch are restored from the row by this test's values.
            setTenant(connection, UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
                    UUID.fromString("00000000-0000-0000-0000-0000000000b1"));
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO payment.payment_approvals
                        (payment_id, status, maker_user_id, matrix_rule_id, submitted_for_approval_at, expires_at)
                    VALUES (?, 'PENDING_APPROVAL', 'maker-1', ?, now(), now() + interval '24 hours') RETURNING id
                    """)) {
                insert.setObject(1, paymentId);
                insert.setObject(2, ruleId);
                try (ResultSet result = insert.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    return (UUID) result.getObject(1);
                }
            }
        }
    }

    private static int queryApprovalCountAsApp(PostgreSQLContainer<?> postgres, UUID tenant, UUID branch) throws Exception {
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), "sepa_app", "dev-only-app")) {
            setTenant(connection, tenant, branch);
            try (Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("SELECT count(*) FROM payment.payment_approvals")) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static int queryApprovalCountWithoutTenant(PostgreSQLContainer<?> postgres) throws Exception {
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), "sepa_app", "dev-only-app");
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT count(*) FROM payment.payment_approvals")) {
            result.next();
            return result.getInt(1);
        }
    }

    private static void updateMatrixReferenceAsApp(PostgreSQLContainer<?> postgres, UUID tenant, UUID branch,
            UUID approvalId, UUID replacement) throws Exception {
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), "sepa_app", "dev-only-app")) {
            setTenant(connection, tenant, branch);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE payment.payment_approvals SET matrix_rule_id = ? WHERE id = ?")) {
                update.setObject(1, replacement);
                update.setObject(2, approvalId);
                update.executeUpdate();
            }
        }
    }

    private static void insertRejectedWithBlankCommentAsAdmin(PostgreSQLContainer<?> postgres, UUID paymentId)
            throws Exception {
        try (Connection connection = admin(postgres); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO payment.payment_approvals (payment_id, status, maker_user_id, checker_user_id, decision_comment, decided_at) VALUES ('"
                    + paymentId + "', 'REJECTED', 'maker-2', 'checker-2', ' ', now())");
        }
    }

    private static UUID insertPaymentAsAdmin(PostgreSQLContainer<?> postgres, UUID tenant, UUID branch) throws Exception {
        try (Connection connection = admin(postgres); PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO payment.payments (tenant_id, branch_id, amount, currency, debtor_iban, creditor_iban)
                VALUES (?, ?, 13.00, 'EUR', 'DEBTOR', 'CREDITOR') RETURNING id
                """)) {
            insert.setObject(1, tenant);
            insert.setObject(2, branch);
            try (ResultSet result = insert.executeQuery()) {
                assertThat(result.next()).isTrue();
                return (UUID) result.getObject(1);
            }
        }
    }

    private static void insertSelfApprovedAsAdmin(PostgreSQLContainer<?> postgres, UUID paymentId) throws Exception {
        try (Connection connection = admin(postgres); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO payment.payment_approvals (payment_id, status, maker_user_id, checker_user_id, decided_at) VALUES ('"
                    + paymentId + "', 'APPROVED', 'same-user', 'same-user', now())");
        }
    }

    private static void insertMatrixRuleAsApp(PostgreSQLContainer<?> postgres, UUID tenant) throws Exception {
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), "sepa_app", "dev-only-app")) {
            setTenant(connection, tenant, null);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO reference_data.approval_matrix_rules (tenant_id, valid_from) VALUES (?, CURRENT_DATE)")) {
                insert.setObject(1, tenant);
                insert.executeUpdate();
            }
        }
    }

    private static void insertApprovalAsReferenceDataRole(PostgreSQLContainer<?> postgres, UUID paymentId)
            throws Exception {
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), "reference_data_role",
                "dev-only-reference-data"); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO payment.payment_approvals (payment_id, status, maker_user_id) VALUES ('"
                    + paymentId + "', 'NOT_REQUIRED', 'maker-3')");
        }
    }

    private static void setTenant(Connection connection, UUID tenant, UUID branch) throws SQLException {
        try (PreparedStatement set = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, false), set_config('app.branch_id', ?, false)")) {
            set.setString(1, tenant.toString());
            set.setString(2, branch == null ? "" : branch.toString());
            set.execute();
        }
    }
}

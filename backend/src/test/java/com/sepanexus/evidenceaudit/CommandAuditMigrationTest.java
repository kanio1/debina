package com.sepanexus.evidenceaudit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL 18 proof for immutable, source-owned application command audit. */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class CommandAuditMigrationTest {

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID BRANCH_A = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");

    @BeforeAll
    static void migrateFreshDatabase() throws Exception {
        try (Connection admin = admin(); Statement statement = admin.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        migrate(POSTGRES, null);
    }

    @Test
    void sourceOwnedAuditTableIsForcedRlsAppendOnlyAndNarrowlyCallable() throws Exception {
        try (Connection admin = admin(); Statement statement = admin.createStatement(); ResultSet result = statement.executeQuery("""
                SELECT to_regclass('audit.audit_log'), c.relrowsecurity, c.relforcerowsecurity,
                       pg_get_userbyid(c.relowner),
                       has_table_privilege('sepa_app', 'audit.audit_log', 'INSERT'),
                       has_table_privilege('sepa_app', 'audit.audit_log', 'UPDATE'),
                       has_table_privilege('sepa_app', 'audit.audit_log', 'DELETE'),
                       has_table_privilege('sepa_app', 'audit.audit_log', 'TRUNCATE'),
                       NOT EXISTS (
                           SELECT FROM pg_proc p CROSS JOIN LATERAL aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) acl
                           WHERE p.oid = 'audit.append_command_audit(uuid,uuid,text,text,text,text,uuid,text,text,uuid,uuid,uuid,text,jsonb,jsonb,text,uuid,timestamptz)'::regprocedure
                             AND acl.grantee = 0 AND acl.privilege_type = 'EXECUTE'
                       ),
                       has_function_privilege('sepa_app', 'audit.append_command_audit(uuid,uuid,text,text,text,text,uuid,text,text,uuid,uuid,uuid,text,jsonb,jsonb,text,uuid,timestamptz)', 'EXECUTE')
                FROM pg_class c WHERE c.oid = 'audit.audit_log'::regclass
                """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("audit.audit_log");
            assertThat(result.getBoolean(2)).isTrue();
            assertThat(result.getBoolean(3)).isTrue();
            assertThat(result.getString(4)).isEqualTo("audit_command_append_owner");
            for (int column = 5; column <= 8; column++) assertThat(result.getBoolean(column)).isFalse();
            assertThat(result.getBoolean(9)).isTrue();
            assertThat(result.getBoolean(10)).isTrue();
        }
    }

    @Test
    void typedAppendHasCanonicalStatesAndProvesTenantBranchAuditorAndMutationBoundaries() throws Exception {
        UUID first = appendAsApp(TENANT_A, BRANCH_A, "maker-a");
        appendAsApp(TENANT_B, null, "maker-b");

        try (Connection app = app(); Statement statement = app.createStatement()) {
            setContext(app, TENANT_A, BRANCH_A, null);
            assertThat(count(statement)).isEqualTo(1);
            try (ResultSet json = statement.executeQuery("SELECT before_state::text, after_state::text FROM audit.audit_log")) {
                assertThat(json.next()).isTrue();
                assertThat(json.getString(1)).isEqualTo("{\"approvalId\": \"00000000-0000-0000-0000-0000000000d1\", \"approvalStatus\": \"PENDING_APPROVAL\"}");
                assertThat(json.getString(2)).isEqualTo("{\"approvalId\": \"00000000-0000-0000-0000-0000000000d1\", \"approvalStatus\": \"APPROVED\"}");
            }
            setContext(app, TENANT_A, UUID.randomUUID(), null);
            assertThat(count(statement)).isZero();
            setContext(app, null, null, "auditor");
            assertThat(count(statement)).isZero();
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE audit.audit_log SET actor_id = 'changed' WHERE audit_entry_id = '" + first + "'"))
                    .isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM audit.audit_log WHERE audit_entry_id = '" + first + "'"))
                    .isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
            assertThatThrownBy(() -> statement.execute("TRUNCATE audit.audit_log"))
                    .isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
        }
        try (Connection auditor = auditor(); Statement statement = auditor.createStatement()) {
            setContext(auditor, null, null, "auditor");
            assertThat(count(statement)).isEqualTo(2);
            assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO audit.audit_log (tenant_id, actor_type, actor_id, authorized_role, correlation_id, command_type, target_type, target_id, before_state, after_state, outcome, command_execution_id, occurred_at) VALUES ('"
                    + TENANT_A + "', 'HUMAN', 'x', 'payment_approver', '" + UUID.randomUUID() + "', 'x', 'x', '"
                    + UUID.randomUUID() + "', '{}', '{}', 'SUCCESS', '" + UUID.randomUUID() + "', now())"))
                    .isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
        }
    }

    @Test
    void representativeV54ApprovalRowsSurviveUpgradeToTheAuditSchema() throws Exception {
        try (PostgreSQLContainer<?> upgrade = new PostgreSQLContainer<>("postgres:18")
                .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin")) {
            upgrade.start();
            try (Connection admin = DriverManager.getConnection(upgrade.getJdbcUrl(), "test_admin", "test_admin"); Statement statement = admin.createStatement()) {
                statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
            }
            migrate(upgrade, "54");
            UUID paymentId = UUID.randomUUID();
            try (Connection admin = DriverManager.getConnection(upgrade.getJdbcUrl(), "test_admin", "test_admin"); Statement statement = admin.createStatement()) {
                statement.execute("INSERT INTO payment.payments (id, tenant_id, amount, currency, debtor_iban, creditor_iban, status) VALUES ('" + paymentId + "', '" + TENANT_A + "', 10.00, 'EUR', 'DEBTOR', 'CREDITOR', NULL)");
                statement.execute("INSERT INTO payment.payment_approvals (payment_id, status, maker_user_id, submitted_for_approval_at, expires_at) VALUES ('" + paymentId + "', 'PENDING_APPROVAL', 'maker-a', now(), now() + interval '24 hours')");
            }
            migrate(upgrade, null);
            try (Connection admin = DriverManager.getConnection(upgrade.getJdbcUrl(), "test_admin", "test_admin"); Statement statement = admin.createStatement()) {
                assertThat(count(statement, "SELECT count(*) FROM payment.payment_approvals WHERE payment_id = '" + paymentId + "'")).isEqualTo(1);
                assertThat(count(statement, "SELECT count(*) FROM audit.audit_log")).isZero();
            }
        }
    }

    private static void migrate(PostgreSQLContainer<?> database, String target) {
        var configuration = Flyway.configure().dataSource(database.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration");
        if (target != null) configuration.target(target);
        configuration.load().migrate();
    }

    private static UUID appendAsApp(UUID tenant, UUID branch, String actor) throws Exception {
        try (Connection connection = app()) {
            setContext(connection, tenant, branch, null);
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT audit.append_command_audit(?::uuid, ?::uuid, 'HUMAN'::text, ?, 'payment_approver'::text, 'sid-1'::text, ?::uuid,
                        'PAYMENT_APPROVAL_APPROVE'::text, 'PAYMENT_APPROVAL'::text, ?::uuid, ?::uuid, NULL::uuid, NULL::text,
                        '{"approvalId":"00000000-0000-0000-0000-0000000000d1","approvalStatus":"PENDING_APPROVAL"}'::jsonb,
                        '{"approvalId":"00000000-0000-0000-0000-0000000000d1","approvalStatus":"APPROVED"}'::jsonb,
                        'SUCCESS'::text, ?::uuid, ?::timestamptz)
                    """)) {
                UUID target = UUID.randomUUID();
                statement.setObject(1, tenant); statement.setObject(2, branch); statement.setString(3, actor);
                statement.setObject(4, UUID.randomUUID()); statement.setObject(5, target); statement.setObject(6, target);
                statement.setObject(7, UUID.randomUUID()); statement.setObject(8, java.sql.Timestamp.from(Instant.parse("2026-07-21T10:00:00Z")));
                try (ResultSet result = statement.executeQuery()) { assertThat(result.next()).isTrue(); return (UUID) result.getObject(1); }
            }
        }
    }

    private static long count(Statement statement) throws SQLException { return count(statement, "SELECT count(*) FROM audit.audit_log"); }
    private static long count(Statement statement, String sql) throws SQLException { try (ResultSet result = statement.executeQuery(sql)) { assertThat(result.next()).isTrue(); return result.getLong(1); } }
    private static void setContext(Connection c, UUID tenant, UUID branch, String role) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement("SELECT set_config('app.tenant_id', ?, false), set_config('app.branch_id', ?, false), set_config('app.role', ?, false)")) {
            statement.setString(1, tenant == null ? "" : tenant.toString()); statement.setString(2, branch == null ? "" : branch.toString()); statement.setString(3, role == null ? "" : role); statement.execute();
        }
    }
    private static Connection admin() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
    private static Connection app() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "sepa_app", "dev-only-app"); }
    private static Connection auditor() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "audit_auditor_role", "dev-only-audit-auditor"); }
}

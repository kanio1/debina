package com.sepanexus.evidenceaudit.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sepanexus.evidenceaudit.AuditQueryPort;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL 18 proof that the public port executes scoped keyset queries in the database. */
@Testcontainers
class CommandAuditQueryIntegrationTest {
    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID BRANCH_A = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID BRANCH_B = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");

    private static JdbcAuditQueryPort queryPort;
    private static TransactionTemplate appTransaction;

    @BeforeAll
    static void migrateAndConstructQueryPort() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
        DataSource app = dataSource("sepa_app", "dev-only-app");
        DataSource auditor = dataSource("audit_auditor_role", "dev-only-audit-auditor");
        queryPort = new JdbcAuditQueryPort(new JdbcTemplate(app), new JdbcTemplate(auditor),
                new DataSourceTransactionManager(auditor));
        appTransaction = new TransactionTemplate(new DataSourceTransactionManager(app));
        appTransaction.setReadOnly(true);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ordinaryTenantAndBranchQueryUsesRlsAndSupportsObjectFilters() throws Exception {
        UUID paymentA = UUID.randomUUID();
        UUID paymentB = UUID.randomUUID();
        append(TENANT_A, BRANCH_A, paymentA, "operator-a", Instant.parse("2026-07-21T10:00:00Z"));
        append(TENANT_A, BRANCH_B, paymentB, "operator-b", Instant.parse("2026-07-21T10:01:00Z"));
        append(TENANT_B, BRANCH_A, UUID.randomUUID(), "operator-c", Instant.parse("2026-07-21T10:02:00Z"));

        AuditQueryPort.AuditPage page = asUser(TENANT_A, BRANCH_A, "operator", () ->
                appTransaction.execute(status -> queryPort.paymentTrail(paymentA, 25, null)));

        assertThat(page.items()).extracting(AuditQueryPort.AuditEntry::paymentId).containsExactly(paymentA);
        assertThat(page.items().getFirst().tenantId()).isEqualTo(TENANT_A);
        assertThat(page.items().getFirst().branchId()).isEqualTo(BRANCH_A);
        assertThat(page.items().getFirst().beforeState().approvalStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(page.items().getFirst().afterState().approvalStatus()).isEqualTo("APPROVED");
    }

    @Test
    void cursorIsStableForEqualTimestampsAndNewRowsDoNotCorruptTraversal() throws Exception {
        Instant sameTime = Instant.parse("2026-07-21T11:00:00Z");
        UUID payment = UUID.randomUUID();
        UUID first = append(TENANT_A, BRANCH_A, payment, "operator-a", sameTime);
        UUID second = append(TENANT_A, BRANCH_A, payment, "operator-a", sameTime);
        UUID third = append(TENANT_A, BRANCH_A, payment, "operator-a", sameTime);

        AuditQueryPort.AuditPage pageOne = asUser(TENANT_A, BRANCH_A, "operator", () ->
                appTransaction.execute(status -> queryPort.paymentTrail(payment, 2, null)));
        append(TENANT_A, BRANCH_A, payment, "operator-a", sameTime.plusSeconds(1));
        AuditQueryPort.AuditPage pageTwo = asUser(TENANT_A, BRANCH_A, "operator", () ->
                appTransaction.execute(status -> queryPort.paymentTrail(payment, 2, pageOne.nextCursor())));

        assertThat(pageOne.items()).hasSize(2);
        assertThat(pageTwo.items()).hasSize(1);
        assertThat(List.of(pageOne.items().get(0).auditEntryId(), pageOne.items().get(1).auditEntryId(),
                pageTwo.items().getFirst().auditEntryId())).containsExactlyInAnyOrder(first, second, third);
        assertThat(pageTwo.items()).noneMatch(entry -> pageOne.items().stream()
                .anyMatch(firstPageEntry -> firstPageEntry.auditEntryId().equals(entry.auditEntryId())));
        assertThatThrownBy(() -> asUser(TENANT_A, BRANCH_A, "operator", () ->
                appTransaction.execute(status -> queryPort.paymentTrail(payment, 1, "not-a-cursor"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid audit cursor");
    }

    @Test
    void auditorCanSearchAcrossTenantsButReceivesNoWritePath() throws Exception {
        UUID paymentA = UUID.randomUUID();
        UUID paymentB = UUID.randomUUID();
        append(TENANT_A, BRANCH_A, paymentA, "maker-a", Instant.parse("2026-07-21T12:00:00Z"));
        append(TENANT_B, BRANCH_B, paymentB, "maker-b", Instant.parse("2026-07-21T12:01:00Z"));

        AuditQueryPort.AuditPage page = asUser(TENANT_A, BRANCH_A, "auditor", () ->
                queryPort.search(new AuditQueryPort.AuditSearchFilter(null, null, null, null, null, null,
                        null, "PAYMENT_APPROVAL_APPROVE", null, null, Instant.parse("2026-07-21T11:30:00Z"), null), 1_000, null));

        assertThat(page.items()).extracting(AuditQueryPort.AuditEntry::tenantId).contains(TENANT_A, TENANT_B);
        assertThat(page.items()).hasSize(2);
        try (Connection auditor = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "audit_auditor_role", "dev-only-audit-auditor");
                Statement statement = auditor.createStatement()) {
            statement.execute("SELECT set_config('app.role', 'auditor', false)");
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM audit.audit_log"))
                    .hasMessageContaining("permission denied");
        }
    }

    private static UUID append(UUID tenant, UUID branch, UUID payment, String actor, Instant occurredAt) throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "sepa_app", "dev-only-app")) {
            try (PreparedStatement context = connection.prepareStatement(
                    "SELECT set_config('app.tenant_id', ?, false), set_config('app.branch_id', ?, false)")) {
                context.setString(1, tenant.toString()); context.setString(2, branch.toString()); context.execute();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT audit.append_command_audit(?::uuid, ?::uuid, 'HUMAN', ?, 'operator', 'sid', ?::uuid,
                        'PAYMENT_APPROVAL_APPROVE', 'PAYMENT_APPROVAL', ?::uuid, ?::uuid, NULL, 'approved',
                        '{"approvalId":"before","approvalStatus":"PENDING_APPROVAL","secret":"not-exposed"}'::jsonb,
                        '{"approvalId":"after","approvalStatus":"APPROVED","secret":"not-exposed"}'::jsonb,
                        'SUCCESS', ?::uuid, ?::timestamptz)
                    """)) {
                UUID target = UUID.randomUUID();
                statement.setObject(1, tenant); statement.setObject(2, branch); statement.setString(3, actor);
                statement.setObject(4, UUID.randomUUID()); statement.setObject(5, target); statement.setObject(6, payment);
                statement.setObject(7, UUID.randomUUID()); statement.setTimestamp(8, java.sql.Timestamp.from(occurredAt));
                try (ResultSet result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    return (UUID) result.getObject(1);
                }
            }
        }
    }

    private static <T> T asUser(UUID tenant, UUID branch, String role, java.util.concurrent.Callable<T> operation) throws Exception {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), java.util.Map.of("alg", "none"),
                java.util.Map.of("sub", "test-" + role, "tenant_id", tenant.toString(), "branch_id", branch.toString()));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        return operation.call();
    }

    private static DataSource dataSource(String username, String password) {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl()); source.setUser(username); source.setPassword(password);
        return source;
    }

    private static Connection admin() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}

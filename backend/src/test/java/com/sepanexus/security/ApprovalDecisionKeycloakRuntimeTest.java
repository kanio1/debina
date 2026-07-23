package com.sepanexus.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.sepanexus.SepaNexusApplication;
import com.sepanexus.modules.paymentlifecycle.service.ApprovalDecisionCommand;
import com.sepanexus.modules.paymentlifecycle.service.ApprovalDecisionService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** A Keycloak-issued, issuer-validated token must traverse the decision authorization boundary. */
@SpringBootTest(classes = SepaNexusApplication.class)
@org.junit.jupiter.api.Tag("testcontainers")
class ApprovalDecisionKeycloakRuntimeTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");
    private static boolean initialized;

    @Autowired
    private ApprovalDecisionService decisions;

    @Autowired
    private JwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        initializeDatabase();
        KeycloakRealmTestSupport.start();
        try {
            KeycloakRealmTestSupport.createDirectGrantProbeClient();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create Keycloak runtime probe client", exception);
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "sepa_app");
        registry.add("spring.datasource.password", () -> "dev-only-app");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "sepa_migration");
        registry.add("spring.flyway.password", () -> "dev-only-migration");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", KeycloakRealmTestSupport::issuerUri);
    }

    @BeforeEach
    void clearState() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE audit.audit_log, payment.payment_approvals, payment.payments, payment.outbox_events, "
                    + "payment.payment_status_history, payment.payment_events, ingress.idempotency_keys, "
                    + "ingress.raw_inbound_messages, iso.payment_iso_identifiers, iso.message_lineage, "
                    + "iso.iso_messages, reference_data.approval_matrix_rules CASCADE");
        }
    }

    @Test
    void realApproverTokenCanApproveAnotherMakersPendingPayment() throws Exception {
        UUID payment = pendingPayment();
        String token = KeycloakRealmTestSupport.passwordGrantToken("approver", "dev-only-approver");
        var jwt = jwtDecoder.decode(token);
        assertThat(jwt.getClaims()).containsKey("sub");
        var authentication = new SecurityConfig().jwtAuthenticationConverter().convert(jwt);
        assertThat(authentication.getAuthorities()).extracting(Object::toString).contains("ROLE_payment_approver");

        var context = SecurityContextHolder.getContext();
        var previous = context.getAuthentication();
        context.setAuthentication(authentication);
        try {
            var result = decisions.decide(new ApprovalDecisionCommand(TENANT, BRANCH, payment, jwt.getSubject(),
                    jwt.getClaimAsString("sid"), UUID.randomUUID(), "keycloak-runtime-approve", null,
                    ApprovalDecisionCommand.Decision.APPROVE));
            assertThat(result.approvalStatus().name()).isEqualTo("APPROVED");
        } finally {
            context.setAuthentication(previous);
        }

        assertThat(count("SELECT count(*) FROM audit.audit_log WHERE payment_id = ? AND actor_id = ?", payment, jwt.getSubject())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.outbox_events WHERE aggregate_id = ?", payment)).isEqualTo(1);
    }

    private static UUID pendingPayment() throws Exception {
        UUID rule;
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "reference_data_role", "dev-only-reference-data")) {
            try (PreparedStatement guc = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
                guc.setString(1, TENANT.toString()); guc.execute();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO reference_data.approval_matrix_rules (tenant_id, requires_approval, requires_step_up, valid_from)
                    VALUES (?, true, false, CURRENT_DATE) RETURNING id
                    """)) {
                insert.setObject(1, TENANT);
                try (ResultSet result = insert.executeQuery()) { result.next(); rule = (UUID) result.getObject(1); }
            }
        }
        try (Connection connection = admin(); PreparedStatement payment = connection.prepareStatement("""
                INSERT INTO payment.payments (tenant_id, branch_id, amount, currency, debtor_iban, creditor_iban, status)
                VALUES (?, ?, 10.00, 'EUR', 'DEBTOR', 'CREDITOR', NULL) RETURNING id
                """)) {
            payment.setObject(1, TENANT); payment.setObject(2, BRANCH);
            try (ResultSet result = payment.executeQuery()) {
                result.next(); UUID paymentId = (UUID) result.getObject(1);
                try (PreparedStatement approval = connection.prepareStatement("""
                        INSERT INTO payment.payment_approvals
                            (payment_id, status, maker_user_id, matrix_rule_id, submitted_for_approval_at, expires_at)
                        VALUES (?, 'PENDING_APPROVAL', 'keycloak-maker', ?, ?, ?)
                        """)) {
                    approval.setObject(1, paymentId); approval.setObject(2, rule);
                    approval.setTimestamp(3, java.sql.Timestamp.from(Instant.now()));
                    approval.setTimestamp(4, java.sql.Timestamp.from(Instant.now().plusSeconds(3600)));
                    approval.executeUpdate();
                }
                return paymentId;
            }
        }
    }

    private static int count(String sql, Object... values) throws Exception {
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getInt(1); }
        }
    }

    static synchronized void initializeDatabase() {
        if (initialized) return;
        POSTGRES.start();
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        } catch (Exception exception) { throw new IllegalStateException("Cannot initialize PostgreSQL test container", exception); }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
        initialized = true;
    }

    private static Connection admin() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}

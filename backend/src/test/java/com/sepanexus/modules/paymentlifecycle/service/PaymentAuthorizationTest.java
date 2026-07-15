package com.sepanexus.modules.paymentlifecycle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sepanexus.SepaNexusApplication;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = SepaNexusApplication.class)
class PaymentAuthorizationTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");
    private static boolean initialized;

    @Autowired
    private PaymentService paymentService;

    private UUID paymentId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        initializeDatabase();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "sepa_app");
        registry.add("spring.datasource.password", () -> "dev-only-app");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "sepa_migration");
        registry.add("spring.flyway.password", () -> "dev-only-migration");
    }

    @BeforeEach
    void seedPayment() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE payment.payments");
        }
        paymentId = insertPayment(TENANT, "e2e-oq14");
    }

    @Test
    @WithMockUser(roles = "operator")
    void rejectsOperatorForPaymentSubmission() {
        assertThatThrownBy(() -> paymentService.submitPayment(command()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "payment_viewer")
    void rejectsPaymentViewerForPaymentSubmission() {
        assertThatThrownBy(() -> paymentService.submitPayment(command()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "payment_viewer", "payment_submitter", "payment_approver", "operator", "auditor" })
    void workspaceTwoRolesCanListPayments(String role) {
        withRole(role, () -> assertThat(paymentService.visiblePayments(TENANT.toString()))
                .extracting(payment -> payment.getEndToEndId())
                .containsExactly("e2e-oq14"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "payment_viewer", "payment_submitter", "payment_approver", "operator", "auditor" })
    void workspaceTwoRolesCanReadPaymentDetail(String role) {
        withRole(role, () -> assertThat(paymentService.paymentDetail(TENANT.toString(), paymentId).payment()
                .getEndToEndId()).isEqualTo("e2e-oq14"));
    }

    @Test
    @WithMockUser(roles = "settlement_operator")
    void rejectsRoleOutsideWorkspaceTwoForPaymentList() {
        assertThatThrownBy(() -> paymentService.visiblePayments(TENANT.toString()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "settlement_operator")
    void rejectsRoleOutsideWorkspaceTwoForPaymentDetail() {
        assertThatThrownBy(() -> paymentService.paymentDetail(TENANT.toString(), paymentId))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void withRole(String role, Runnable assertion) {
        var authorities = java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_" + role));
        var authentication = org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                .authenticated("test-user", null, authorities);
        var previous = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication();
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(authentication);
        try {
            assertion.run();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(previous);
        }
    }

    private static SubmitPaymentCommand command() {
        return new SubmitPaymentCommand(UUID.randomUUID(), null, "E2E-1", new BigDecimal("1.00"), "EUR", "DEBTOR",
                "CREDITOR", UUID.randomUUID().toString());
    }

    static synchronized void initializeDatabase() {
        if (initialized) {
            return;
        }
        POSTGRES.start();
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot initialize PostgreSQL test container", exception);
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
        initialized = true;
    }

    private static Connection adminConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }

    private static UUID insertPayment(UUID tenantId, String endToEndId) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment.payments (id, tenant_id, end_to_end_id, amount, debtor_iban, creditor_iban)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, tenantId);
            statement.setString(3, endToEndId);
            statement.setBigDecimal(4, new BigDecimal("10.00"));
            statement.setString(5, "DE89370400440532013000");
            statement.setString(6, "FR7630006000011234567890189");
            statement.executeUpdate();
        }
        return id;
    }
}

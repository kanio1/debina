package com.sepanexus.modules.paymentlifecycle.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = SepaNexusApplication.class)
@org.junit.jupiter.api.Tag("testcontainers")
class TenantGucIntegrationTest {

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");
    private static boolean initialized;

    @Autowired
    protected PaymentService paymentService;

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
    void seedTenantRows() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE payment.payment_approvals, payment.payments, iso.payment_iso_identifiers, iso.message_lineage, "
                    + "iso.iso_messages CASCADE");
        }
        insertPayment(TENANT_A, "e2e-tenant-a");
        insertPayment(TENANT_B, "e2e-tenant-b");
    }

    @Test
    @WithMockUser(roles = "payment_submitter")
    void twoConsecutiveTenantRequestsSeeOnlyTheirOwnRows() {
        assertThat(paymentService.visiblePayments(TENANT_A.toString()))
                .extracting(payment -> payment.endToEndId())
                .containsExactly("e2e-tenant-a");
        assertThat(paymentService.visiblePayments(TENANT_B.toString()))
                .extracting(payment -> payment.endToEndId())
                .containsExactly("e2e-tenant-b");
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

    private static void insertPayment(UUID tenantId, String endToEndId) throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID isoMessageId = UUID.randomUUID();
        try (Connection connection = adminConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO payment.payments (id, tenant_id, amount, debtor_iban, creditor_iban)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                statement.setObject(1, paymentId);
                statement.setObject(2, tenantId);
                statement.setBigDecimal(3, new BigDecimal("10.00"));
                statement.setString(4, "DE89370400440532013000");
                statement.setString(5, "FR7630006000011234567890189");
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO iso.iso_messages (id, direction, message_type, parse_status)
                    VALUES (?, 'INBOUND', 'JSON_DIRECT', 'SKIPPED')
                    """)) {
                statement.setObject(1, isoMessageId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO iso.payment_iso_identifiers (payment_id, source_message_type, iso_message_id, end_to_end_id)
                    VALUES (?, 'JSON_DIRECT', ?, ?)
                    """)) {
                statement.setObject(1, paymentId);
                statement.setObject(2, isoMessageId);
                statement.setString(3, endToEndId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO iso.message_lineage (lineage_role, iso_message_id, payment_id)
                    VALUES ('ORIGINAL_INSTRUCTION', ?, ?)
                    """)) {
                statement.setObject(1, isoMessageId);
                statement.setObject(2, paymentId);
                statement.executeUpdate();
            }
        }
    }

}

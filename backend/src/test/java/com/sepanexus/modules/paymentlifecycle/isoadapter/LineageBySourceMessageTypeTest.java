package com.sepanexus.modules.paymentlifecycle.isoadapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.sepanexus.SepaNexusApplication;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

import com.sepanexus.modules.paymentlifecycle.service.PaymentService;
import com.sepanexus.modules.paymentlifecycle.service.SubmitPaymentCommand;

/**
 * EPIC-21 Story 21.3: a payment referenced by more than one source ISO message type carries one
 * identifier/lineage row per {@code source_message_type} (sepa-nexus-message-flow-and-data-blueprint.md
 * §4.3: "a payment can be referenced by more than one source message type, not one flat set of
 * columns"). Today only {@code JSON_DIRECT} is a real producer (pacs.002/R-message correlation is
 * EPIC-26/EPIC-ISO-2, not built yet) — this test proves the composite-key model itself holds by
 * inserting a second, independent lineage row for a different {@code source_message_type} against
 * the same payment and confirming both coexist without collision.
 */
@SpringBootTest(classes = SepaNexusApplication.class)
@org.junit.jupiter.api.Tag("testcontainers")
class LineageBySourceMessageTypeTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");
    private static boolean initialized;

    @Autowired
    private PaymentService paymentService;

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
    void cleanTables() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE payment.payment_approvals, payment.payments, payment.outbox_events, "
                    + "ingress.idempotency_keys, ingress.raw_inbound_messages, "
                    + "iso.payment_iso_identifiers, iso.message_lineage, iso.iso_messages CASCADE");
        }
    }

    @Test
    @WithMockUser(roles = "payment_submitter")
    void secondSourceMessageTypeCoexistsWithJsonDirectLineage() throws Exception {
        UUID tenantId = UUID.randomUUID();
        SubmitPaymentCommand command = new SubmitPaymentCommand(tenantId, null, "e2e-lineage-multi-1",
                new BigDecimal("10.00"), "EUR", "DE89370400440532013000", "FR7630006000011234567890189",
                UUID.randomUUID().toString());
        UUID paymentId = paymentService.submitPayment(command).getId();

        // Simulate a later R-message (e.g. camt.056 recall) referencing the same payment under a
        // different source_message_type — this is exactly the composite-PK scenario G4 exists for.
        UUID secondIsoMessageId = UUID.randomUUID();
        try (Connection connection = adminConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO iso.iso_messages (id, direction, message_type, parse_status)
                    VALUES (?, 'INBOUND', 'camt.056', 'OK')
                    """)) {
                statement.setObject(1, secondIsoMessageId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO iso.payment_iso_identifiers (payment_id, source_message_type, iso_message_id, end_to_end_id)
                    VALUES (?, 'camt.056', ?, ?)
                    """)) {
                statement.setObject(1, paymentId);
                statement.setObject(2, secondIsoMessageId);
                statement.setString(3, "e2e-lineage-multi-1");
                statement.executeUpdate();
            }
        }

        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT source_message_type FROM iso.payment_iso_identifiers WHERE payment_id = ?
                """)) {
            statement.setObject(1, paymentId);
            try (ResultSet result = statement.executeQuery()) {
                java.util.Set<String> sourceMessageTypes = new java.util.HashSet<>();
                while (result.next()) {
                    sourceMessageTypes.add(result.getString(1));
                }
                assertThat(sourceMessageTypes).containsExactlyInAnyOrder("JSON_DIRECT", "camt.056");
            }
        }
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
}

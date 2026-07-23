package com.sepanexus.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Fresh-install proof: V20 baseline creation followed by the forward correction never marks an FSM state final. */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class PaymentHistoryFreshMigrationFinalityTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");

    private static UUID received;
    private static UUID validated;
    private static UUID rejected;
    private static UUID dispatched;

    @BeforeAll
    static void seedPaymentsBeforeHistoryMigrationThenMigrateFully() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        migrate("19");
        received = insertPayment("RECEIVED");
        validated = insertPayment("VALIDATED");
        rejected = insertPayment("REJECTED");
        dispatched = insertPayment("DISPATCHED");
        migrate(null);
    }

    @Test
    void freshHistoryBackfillDoesNotTurnAnyBusinessStatusIntoFinality() throws Exception {
        assertThat(isFinal(received)).isFalse();
        assertThat(isFinal(validated)).isFalse();
        assertThat(isFinal(rejected)).isFalse();
        assertThat(isFinal(dispatched)).isFalse();
    }

    private static void migrate(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private static UUID insertPayment(String status) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment.payments (id, tenant_id, amount, currency, debtor_iban, creditor_iban, status)
                VALUES (?, gen_random_uuid(), 10.00, 'EUR', 'DE1', 'FR1', ?)
                """)) {
            statement.setObject(1, id);
            statement.setString(2, status);
            statement.executeUpdate();
        }
        return id;
    }

    private static boolean isFinal(UUID paymentId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT is_final FROM payment.payment_status_history WHERE payment_id = ?")) {
            statement.setObject(1, paymentId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private static Connection adminConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}

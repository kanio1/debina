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

/** Forward-only proof for correcting V20's terminal-business-status false positives. */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class PaymentHistoryFinalityUpgradePathTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");

    private static UUID received;
    private static UUID validated;
    private static UUID rejected;
    private static UUID dispatched;
    private static UUID runtimeRejected;
    private static UUID unrelatedFinalityEvidence;

    @BeforeAll
    static void migrateThroughPreviousVersionAndSeedLegacyRows() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        migrate("29");
        received = insertLegacyBaseline("RECEIVED", false);
        validated = insertLegacyBaseline("VALIDATED", false);
        rejected = insertLegacyBaseline("REJECTED", true);
        dispatched = insertLegacyBaseline("DISPATCHED", true);
        runtimeRejected = insertHistoryRow("REJECTED", true, "payment.transition.recorded");
        unrelatedFinalityEvidence = insertHistoryRow("RECEIVED", true, "UNRELATED_HISTORY_EVIDENCE");
        migrate(null);
    }

    @Test
    void upgradeClearsOnlyLegacyTerminalityDerivedFinality() throws Exception {
        assertThat(isFinal(received)).isFalse();
        assertThat(isFinal(validated)).isFalse();
        assertThat(isFinal(rejected)).isFalse();
        assertThat(isFinal(dispatched)).isFalse();
        assertThat(isFinal(runtimeRejected)).isFalse();
        assertThat(isFinal(unrelatedFinalityEvidence)).isTrue();
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

    private static UUID insertLegacyBaseline(String status, boolean isFinal) throws Exception {
        return insertHistoryRow(status, isFinal, "MIGRATION_BASELINE");
    }

    private static UUID insertHistoryRow(String status, boolean isFinal, String eventType) throws Exception {
        UUID paymentId = UUID.randomUUID();
        try (Connection connection = adminConnection(); PreparedStatement payment = connection.prepareStatement("""
                INSERT INTO payment.payments (id, tenant_id, amount, currency, debtor_iban, creditor_iban, status)
                VALUES (?, gen_random_uuid(), 10.00, 'EUR', 'DE1', 'FR1', ?)
                """); PreparedStatement history = connection.prepareStatement("""
                INSERT INTO payment.payment_status_history
                    (payment_id, seq, from_status, to_status, status_code, source_type, actor_type, is_final, event_type, at)
                VALUES (?, 1, NULL, ?, ?, 'INTERNAL', 'SYSTEM', ?, ?, now())
                """)) {
            payment.setObject(1, paymentId);
            payment.setString(2, status);
            payment.executeUpdate();
            history.setObject(1, paymentId);
            history.setString(2, status);
            history.setString(3, status);
            history.setBoolean(4, isFinal);
            history.setString(5, eventType);
            history.executeUpdate();
        }
        return paymentId;
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

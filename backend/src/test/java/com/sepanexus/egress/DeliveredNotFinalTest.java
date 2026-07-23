package com.sepanexus.egress;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL 18 proof that an egress delivery transition is transport-only, never finality. */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class DeliveredNotFinalTest {

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");

    @Test void deliveredByTheEgressWriterLeavesPaymentFinalityAndAuthorityRecordsUntouched() throws Exception {
        migrate();
        UUID paymentId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant finality = Instant.parse("2026-07-20T10:00:00Z");
        insertFinalPayment(paymentId, finality);
        insertRequestedMessage(messageId);

        try (Connection connection = egress(); PreparedStatement statement = connection.prepareStatement("""
                UPDATE egress.outbound_messages SET state = 'DELIVERED' WHERE id = ?
                """)) {
            statement.setObject(1, messageId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }

        assertThat(messageState(messageId)).isEqualTo("DELIVERED");
        assertThat(paymentFinality(paymentId)).isEqualTo(finality);
        assertThat(finalityRecordCount(paymentId)).isZero();
    }

    private static void migrate() throws Exception {
        try (Connection connection = admin(); var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
    }

    private static void insertFinalPayment(UUID paymentId, Instant finality) throws Exception {
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment.payments
                    (id, tenant_id, amount, currency, debtor_iban, creditor_iban, status, finality_at)
                VALUES (?, gen_random_uuid(), 10.00, 'EUR', 'DE1', 'FR1', 'VALIDATED', ?)
                """)) {
            statement.setObject(1, paymentId);
            statement.setObject(2, java.sql.Timestamp.from(finality));
            statement.executeUpdate();
        }
    }

    private static void insertRequestedMessage(UUID messageId) throws Exception {
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO egress.outbound_messages
                    (id, tenant_id, recipient_id, channel, artifact_type, correlation_msg_id, payload, payload_sha256)
                VALUES (?, gen_random_uuid(), gen_random_uuid(), 'WEBHOOK', 'PACS002', 'delivery-only',
                        'payload'::bytea, sha256('payload'::bytea))
                """)) {
            statement.setObject(1, messageId);
            statement.executeUpdate();
        }
    }

    private static String messageState(UUID messageId) throws Exception {
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                SELECT state FROM egress.outbound_messages WHERE id = ?
                """)) {
            statement.setObject(1, messageId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private static Instant paymentFinality(UUID paymentId) throws Exception {
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                SELECT finality_at FROM payment.payments WHERE id = ?
                """)) {
            statement.setObject(1, paymentId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getTimestamp(1).toInstant();
            }
        }
    }

    private static int finalityRecordCount(UUID paymentId) throws Exception {
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) FROM settlement.settlement_finality_records WHERE payment_id = ?
                """)) {
            statement.setObject(1, paymentId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static Connection admin() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }

    private static Connection egress() throws Exception {
        Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "egress_role", "dev-only-egress");
        try (var statement = connection.createStatement()) {
            statement.execute("SET app.role = 'system_relay'");
        }
        return connection;
    }
}

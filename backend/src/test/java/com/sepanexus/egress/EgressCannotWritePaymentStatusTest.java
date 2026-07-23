package com.sepanexus.egress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * EPIC-14 Story 14.2 (`[FREEZE]`: egress manages transport state only, never finality):
 * {@code egress_role} has zero grants anywhere on the {@code payment} schema — confirmed by
 * migration audit (no {@code GRANT ... TO egress_role} appears in any {@code payment/*} migration).
 * This proves that fail-closed default with real Testcontainers grant checks, not just by
 * documentation. {@code SELECT} is also denied, fail-closed — no source document grants {@code
 * egress} read access to {@code payment.payments} (the {@code egress_delivery_dashboard} read
 * model, §6.6, sources from {@code outbound_messages}/{@code transport_attempts}/{@code
 * delivery_receipts} only, never {@code payment.payments} directly).
 */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class EgressCannotWritePaymentStatusTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin")
            .withStartupAttempts(3);

    private static UUID seededPaymentId;

    @BeforeAll
    static void migrateFreshDatabase() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();

        seededPaymentId = UUID.randomUUID();
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO payment.payments (id, tenant_id, amount, currency, debtor_iban, creditor_iban, status)
                    VALUES ('%s', gen_random_uuid(), 10.00, 'EUR', 'DE1', 'FR1', 'RECEIVED')
                    """.formatted(seededPaymentId));
        }
    }

    @Test
    void egressRoleHasNoUsageGrantOnPaymentSchema() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            var result = statement.executeQuery(
                    "SELECT has_schema_privilege('egress_role', 'payment', 'USAGE')");
            result.next();
            assertThat(result.getBoolean(1)).isFalse();
        }
    }

    @Test
    void egressRoleCannotSelectPaymentPayments() {
        assertThatThrownBy(() -> {
            try (Connection connection = egressConnection(); Statement statement = connection.createStatement()) {
                statement.executeQuery("SELECT id FROM payment.payments WHERE id = '%s'".formatted(seededPaymentId));
            }
        }).isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
    }

    @Test
    void egressRoleCannotInsertIntoPaymentPayments() {
        assertThatThrownBy(() -> {
            try (Connection connection = egressConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO payment.payments (id, tenant_id, amount, currency, debtor_iban, creditor_iban, status)
                        VALUES (gen_random_uuid(), gen_random_uuid(), 10.00, 'EUR', 'DE1', 'FR1', 'RECEIVED')
                        """);
            }
        }).isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
    }

    @Test
    void egressRoleCannotUpdatePaymentStatusColumn() {
        assertThatThrownBy(() -> {
            try (Connection connection = egressConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "UPDATE payment.payments SET status = 'DISPATCHED' WHERE id = '%s'".formatted(seededPaymentId));
            }
        }).isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
    }

    @Test
    void egressDeliveryOrReceiptPathCannotWritePaymentHistoryFinality() {
        assertThatThrownBy(() -> {
            try (Connection connection = egressConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO payment.payment_status_history
                            (payment_id, seq, from_status, to_status, status_code, source_type, actor_type, is_final,
                             event_type, at)
                        VALUES ('%s', 999, 'DISPATCHED', 'DISPATCHED', 'DISPATCHED', 'EGRESS', 'SYSTEM', true,
                                'egress.receipt.received', now())
                        """.formatted(seededPaymentId));
            }
        }).isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
    }

    @Test
    void egressRoleCannotDeleteFromPaymentPayments() {
        assertThatThrownBy(() -> {
            try (Connection connection = egressConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM payment.payments WHERE id = '%s'".formatted(seededPaymentId));
            }
        }).isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
    }

    @Test
    void egressRoleCannotTruncatePaymentPayments() {
        assertThatThrownBy(() -> {
            try (Connection connection = egressConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("TRUNCATE payment.payment_approvals, payment.payments");
            }
        }).isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
    }

    private static Connection egressConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "egress_role", "dev-only-egress");
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}

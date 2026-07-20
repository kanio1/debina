package com.sepanexus.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Representative V30-to-V34 upgrade proof for the additive finality authority migration. */
@Testcontainers
class FinalityMigrationUpgradePathTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");

    @Test
    void upgradePreservesExistingPaymentDataAndAddsOnlyNullFinalityProjectionFields() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        migrate("30");
        UUID payment = UUID.randomUUID();
        try (Connection connection = admin(); PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO payment.payments (id, tenant_id, amount, currency, debtor_iban, creditor_iban, status)
                VALUES (?, gen_random_uuid(), 42.00, 'EUR', 'DE89370400440532013000',
                        'FR7630006000011234567890189', 'REJECTED')
                """)) {
            insert.setObject(1, payment); insert.executeUpdate();
        }

        migrate(null);

        try (Connection connection = admin(); PreparedStatement select = connection.prepareStatement("""
                SELECT status, amount, finality_at, finality_record_id FROM payment.payments WHERE id = ?
                """)) {
            select.setObject(1, payment);
            try (ResultSet result = select.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("REJECTED");
                assertThat(result.getBigDecimal(2)).hasToString("42.00");
                assertThat(result.getObject(3)).isNull();
                assertThat(result.getObject(4)).isNull();
            }
        }
        try (Connection connection = admin(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("""
                SELECT count(*) FROM reference_data.finality_rules WHERE finality_rule_version = 1
                """)) {
            result.next(); assertThat(result.getInt(1)).isEqualTo(4);
        }
    }

    private void migrate(String target) {
        var configuration = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration");
        if (target != null) configuration.target(target);
        configuration.load().migrate();
    }

    private static Connection admin() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}

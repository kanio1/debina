package com.sepanexus.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Representative V44→V45 forward-only migration proof for the new static route catalog. */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class RouteCandidateCatalogMigrationUpgradePathTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");

    @Test
    void additiveCatalogMigrationPreservesPriorPaymentAndBecomesWritableByItsOwner() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        migrate("44");
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO payment.payments (id, tenant_id, amount, currency, debtor_iban, creditor_iban, status)
                    VALUES ('00000000-0000-0000-0000-000000000451', gen_random_uuid(), 42.00, 'EUR', 'DE1', 'FR1', 'VALIDATED')
                    """);
        }
        migrate(null);
        try (Connection connection = admin(); Statement statement = connection.createStatement();
                var result = statement.executeQuery("SELECT status FROM payment.payments WHERE id = '00000000-0000-0000-0000-000000000451'")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("VALIDATED");
        }
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "reference_data_role", "dev-only-reference-data");
                Statement statement = connection.createStatement()) {
            assertThat(statement.executeUpdate("""
                    INSERT INTO reference_data.profile_route_priorities
                        (scheme, service_level, currency, profile_id, priority)
                    VALUES ('SEPA', 'URGP', 'EUR', gen_random_uuid(), 10)
                    """)).isEqualTo(1);
        }
    }

    private static void migrate(String target) {
        var configuration = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration");
        if (target != null) configuration.target(target);
        configuration.load().migrate();
    }
    private static Connection admin() throws Exception { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
}

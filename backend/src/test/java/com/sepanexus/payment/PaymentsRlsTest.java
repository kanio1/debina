package com.sepanexus.payment;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class PaymentsRlsTest {

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");

    @BeforeAll
    static void migrateDatabase() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void seedTenantRows() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE payment.payments");
        }
        insertPayment(TENANT_A, "e2e-tenant-a");
        insertPayment(TENANT_B, "e2e-tenant-b");
    }

    @Test
    void emptyTenantGucReturnsZeroRows() throws Exception {
        try (Connection connection = applicationConnection()) {
            assertEquals(0, paymentCount(connection));
        }
    }

    @Test
    void tenantCannotReadAnotherTenantsPayments() throws Exception {
        try (Connection connection = applicationConnection()) {
            setTenant(connection, TENANT_A);

            assertEquals(1, paymentCount(connection));
            assertEquals(1, paymentCountForEndToEndId(connection, "e2e-tenant-a"));
            assertEquals(0, paymentCountForEndToEndId(connection, "e2e-tenant-b"));
        }
    }

    private static Connection adminConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }

    private static Connection applicationConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "sepa_app", "dev-only-app");
    }

    private void insertPayment(UUID tenantId, String endToEndId) throws Exception {
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO payment.payments
                         (tenant_id, end_to_end_id, amount, debtor_iban, creditor_iban)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {
            statement.setObject(1, tenantId);
            statement.setString(2, endToEndId);
            statement.setBigDecimal(3, new BigDecimal("10.00"));
            statement.setString(4, "PL61109010140000071219812874");
            statement.setString(5, "PL27114020040000300201355387");
            statement.executeUpdate();
        }
    }

    private static void setTenant(Connection connection, UUID tenantId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            statement.setString(1, tenantId.toString());
            statement.execute();
        }
    }

    private static int paymentCount(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM payment.payments")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static int paymentCountForEndToEndId(Connection connection, String endToEndId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM payment.payments WHERE end_to_end_id = ?")) {
            statement.setString(1, endToEndId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }
}

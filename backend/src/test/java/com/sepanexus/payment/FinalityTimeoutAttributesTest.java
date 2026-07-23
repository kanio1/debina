package com.sepanexus.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL 18 fresh/upgrade proof that timing facts do not imply a business rejection or finality. */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class FinalityTimeoutAttributesTest {
    @Container static final PostgreSQLContainer<?> FRESH = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");
    @Container static final PostgreSQLContainer<?> UPGRADE = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");

    @Test void freshMigrationStoresTimingFactsWithoutChangingBusinessOrFinalityAxes() throws Exception {
        createMigrationRole(FRESH); migrate(FRESH, null);
        UUID payment = payment(FRESH);
        setTimes(FRESH, payment);
        assertAxes(FRESH, payment);
    }

    @Test void upgradeFromV43PreservesExistingPaymentAndAddsIndependentTimingFacts() throws Exception {
        createMigrationRole(UPGRADE); migrate(UPGRADE, "43");
        UUID payment = payment(UPGRADE);
        migrate(UPGRADE, null);
        setTimes(UPGRADE, payment);
        assertAxes(UPGRADE, payment);
    }

    private static void createMigrationRole(PostgreSQLContainer<?> postgres) throws Exception {
        try (Connection c = admin(postgres); var s = c.createStatement()) {
            s.execute("""
                    DO $$ BEGIN
                        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sepa_migration') THEN
                            CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration';
                        END IF;
                    END $$
                    """);
        }
    }

    private static void migrate(PostgreSQLContainer<?> postgres, String target) {
        var config = Flyway.configure().dataSource(postgres.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration");
        if (target != null) config.target(target);
        config.load().migrate();
    }

    private static UUID payment(PostgreSQLContainer<?> postgres) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection c = admin(postgres); PreparedStatement s = c.prepareStatement("""
                INSERT INTO payment.payments (id, tenant_id, amount, currency, debtor_iban, creditor_iban, status)
                VALUES (?, gen_random_uuid(), 10.00, 'EUR', 'DE1', 'FR1', 'VALIDATED')
                """)) {
            s.setObject(1, id); s.executeUpdate();
        }
        return id;
    }

    private static void setTimes(PostgreSQLContainer<?> postgres, UUID payment) throws Exception {
        try (Connection c = admin(postgres); PreparedStatement s = c.prepareStatement("""
                UPDATE payment.payments SET timeout_at = ?, revocation_cutoff = ? WHERE id = ?
                """)) {
            s.setTimestamp(1, Timestamp.from(Instant.parse("2026-07-20T10:00:00Z")));
            s.setTimestamp(2, Timestamp.from(Instant.parse("2026-07-20T10:05:00Z")));
            s.setObject(3, payment); s.executeUpdate();
        }
    }

    private static void assertAxes(PostgreSQLContainer<?> postgres, UUID payment) throws Exception {
        try (Connection c = admin(postgres); PreparedStatement s = c.prepareStatement("""
                SELECT status, finality_at, timeout_at, revocation_cutoff FROM payment.payments WHERE id = ?
                """)) {
            s.setObject(1, payment);
            try (ResultSet r = s.executeQuery()) {
                r.next();
                assertThat(r.getString("status")).isEqualTo("VALIDATED");
                assertThat(r.getObject("finality_at")).isNull();
                assertThat(r.getObject("timeout_at")).isNotNull();
                assertThat(r.getObject("revocation_cutoff")).isNotNull();
            }
        }
    }

    private static Connection admin(PostgreSQLContainer<?> postgres) throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), "test_admin", "test_admin");
    }
}

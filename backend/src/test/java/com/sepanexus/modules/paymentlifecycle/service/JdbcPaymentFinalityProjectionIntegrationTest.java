package com.sepanexus.modules.paymentlifecycle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sepanexus.SepaNexusApplication;
import com.sepanexus.modules.PaymentFinalityPort;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** PostgreSQL/RLS proof for payment's narrow, idempotent finality projection port. */
@SpringBootTest(classes = SepaNexusApplication.class)
@org.junit.jupiter.api.Tag("testcontainers")
class JdbcPaymentFinalityProjectionIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000003901");
    private static final Instant FINALITY_AT = Instant.parse("2026-07-20T10:15:30.123Z");
    private static boolean initialized;

    @Autowired
    private JdbcPaymentFinalityProjection projection;

    private UUID paymentId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        initializeDatabase();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "sepa_app");
        registry.add("spring.datasource.password", () -> "dev-only-app");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "sepa_migration");
        registry.add("spring.flyway.password", () -> "dev-only-migration");
        registry.add("ledger.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("settlement.datasource.url", POSTGRES::getJdbcUrl);
    }

    @BeforeEach
    void seedPayment() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE payment.payments CASCADE");
            statement.execute("DROP TRIGGER IF EXISTS payment_finality_projection_failure ON payment.payments");
            statement.execute("DROP FUNCTION IF EXISTS payment.fail_finality_projection()");
        }
        paymentId = UUID.randomUUID();
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment.payments (id, tenant_id, amount, debtor_iban, creditor_iban)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, paymentId);
            statement.setObject(2, TENANT);
            statement.setBigDecimal(3, new BigDecimal("10.00"));
            statement.setString(4, "DE89370400440532013000");
            statement.setString(5, "FR7630006000011234567890189");
            statement.executeUpdate();
        }
    }

    @Test
    void projectsOnceReplaysIdenticallyAndFailsClosedForAnotherAuthorityRecord() throws Exception {
        UUID recordId = UUID.randomUUID();

        assertThat(projection.project(TENANT, paymentId, recordId, FINALITY_AT))
                .isEqualTo(PaymentFinalityPort.ProjectionResult.PROJECTED);
        assertThat(projection.project(TENANT, paymentId, recordId, FINALITY_AT))
                .isEqualTo(PaymentFinalityPort.ProjectionResult.ALREADY_PROJECTED);
        assertThatThrownBy(() -> projection.project(TENANT, paymentId, UUID.randomUUID(), FINALITY_AT))
                .isInstanceOf(PaymentFinalityProjectionConflictException.class);
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement("""
                SELECT finality_record_id, finality_at FROM payment.payments WHERE id = ?
                """)) {
            statement.setObject(1, paymentId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject(1)).isEqualTo(recordId);
                assertThat(result.getTimestamp(2).toInstant()).isEqualTo(FINALITY_AT);
            }
        }
    }

    @Test
    void failedProjectionRollsBackAndAnIdenticalRetryProjectsAfterTheFailureIsRemoved() throws Exception {
        UUID recordId = UUID.randomUUID();
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE FUNCTION payment.fail_finality_projection() RETURNS trigger AS $$
                    BEGIN RAISE EXCEPTION 'projection fault'; END; $$ LANGUAGE plpgsql
                    """);
            statement.execute("""
                    CREATE TRIGGER payment_finality_projection_failure BEFORE UPDATE OF finality_at
                    ON payment.payments FOR EACH ROW EXECUTE FUNCTION payment.fail_finality_projection()
                    """);
        }

        assertThatThrownBy(() -> projection.project(TENANT, paymentId, recordId, FINALITY_AT))
                .isInstanceOf(RuntimeException.class);
        assertThat(finalityRecordId()).isNull();
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER payment_finality_projection_failure ON payment.payments");
            statement.execute("DROP FUNCTION payment.fail_finality_projection()");
        }

        assertThat(projection.project(TENANT, paymentId, recordId, FINALITY_AT))
                .isEqualTo(PaymentFinalityPort.ProjectionResult.PROJECTED);
        assertThat(finalityRecordId()).isEqualTo(recordId);
    }

    private UUID finalityRecordId() throws Exception {
        try (Connection connection = admin(); PreparedStatement statement = connection.prepareStatement(
                "SELECT finality_record_id FROM payment.payments WHERE id = ?")) {
            statement.setObject(1, paymentId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return (UUID) result.getObject(1);
            }
        }
    }

    static synchronized void initializeDatabase() {
        if (initialized) return;
        POSTGRES.start();
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot initialize PostgreSQL test container", exception);
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
        initialized = true;
    }

    private static Connection admin() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}

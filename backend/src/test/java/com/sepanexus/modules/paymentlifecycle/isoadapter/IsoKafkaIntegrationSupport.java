package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * EPIC-27 Story 27.4: same shape as {@link
 * com.sepanexus.modules.paymentlifecycle.event.KafkaIntegrationSupport} (real, isolated
 * PostgreSQL + Kafka Testcontainers, {@code @SpringBootTest} context so {@link
 * IsoOutboxDispatcher}'s own {@code @Scheduled} bean is real, even though every test calls {@code
 * dispatch()} explicitly rather than relying on the scheduler firing) — duplicated rather than
 * extended because that class is package-private to {@code
 * com.sepanexus.modules.paymentlifecycle.event} and not visible from this package; see {@code
 * sepa-nexus-payments-data-integrity} skill's outbox-inbox guidance on following an existing
 * shape rather than inventing a parallel mechanism.
 */
@SpringBootTest
abstract class IsoKafkaIntegrationSupport {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:latest");
    private static boolean initialized;

    @DynamicPropertySource
    static synchronized void properties(DynamicPropertyRegistry registry) {
        if (!initialized) {
            POSTGRES.start();
            KAFKA.start();
            try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
                statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                    .locations("filesystem:src/main/resources/db/migration").load().migrate();
            initialized = true;
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "sepa_app");
        registry.add("spring.datasource.password", () -> "dev-only-app");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "sepa_migration");
        registry.add("spring.flyway.password", () -> "dev-only-migration");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    static Connection adminConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }

    static void eventually(CheckedAssertion assertion) throws Exception {
        AssertionError last = null;
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            try {
                assertion.check();
                return;
            } catch (AssertionError error) {
                last = error;
                Thread.sleep(100);
            }
        }
        throw last == null ? new AssertionError("Timed out") : last;
    }

    @FunctionalInterface
    interface CheckedAssertion { void check() throws Exception; }
}

package com.sepanexus.modules.paymentlifecycle.event;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest
abstract class KafkaIntegrationSupport {

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
        registry.add("outbox.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("outbox.datasource.username", () -> "outbox_dispatcher_role");
        registry.add("outbox.datasource.password", () -> "dev-only-outbox-dispatcher");
        registry.add("spring.kafka.admin.auto-create", () -> "true");
        registry.add("spring.kafka.listener.auto-startup", () -> "true");
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

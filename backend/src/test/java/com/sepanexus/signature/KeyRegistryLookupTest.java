package com.sepanexus.signature;

import static org.assertj.core.api.Assertions.assertThat;

import com.sepanexus.SepaNexusApplication;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * EPIC-31 Story 31.4: register + lookup by {@code as_of} against a real database, covering the
 * four cases the story requires — active, future, expired, and unknown key. An expired, future,
 * or unknown key must all resolve to {@link Optional#empty()}, never a soft warning
 * (sepa-nexus-signature-module-blueprint.md §8).
 */
@SpringBootTest(classes = SepaNexusApplication.class)
@org.junit.jupiter.api.Tag("testcontainers")
class KeyRegistryLookupTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");
    private static boolean initialized;

    @Autowired
    private KeyRegistryPort keyRegistryPort;

    private final UUID participantId = UUID.randomUUID();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        initializeDatabase();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "sepa_app");
        registry.add("spring.datasource.password", () -> "dev-only-app");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "sepa_migration");
        registry.add("spring.flyway.password", () -> "dev-only-migration");
        registry.add("signature.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("signature.datasource.username", () -> "signature_role");
        registry.add("signature.datasource.password", () -> "dev-only-signature");
    }

    @BeforeEach
    void cleanKeys() throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE signature.signature_keys CASCADE");
        }
    }

    @Test
    void activeKeyIsFound() {
        Instant now = Instant.now();
        keyRegistryPort.register(new SignatureKeyRegistration(participantId, KeyPurpose.VERIFY, "ED25519-SYNTH",
                "pub-active", "ref-active", now.minus(1, ChronoUnit.HOURS), null));

        Optional<SignatureKeyView> found = keyRegistryPort.lookup(participantId, KeyPurpose.VERIFY, now);

        assertThat(found).isPresent();
        assertThat(found.get().algo()).isEqualTo("ED25519-SYNTH");
        assertThat(found.get().status()).isEqualTo(KeyStatus.ACTIVE);
    }

    @Test
    void futureKeyIsNotYetFound() {
        Instant now = Instant.now();
        keyRegistryPort.register(new SignatureKeyRegistration(participantId, KeyPurpose.VERIFY, "ED25519-SYNTH",
                "pub-future", "ref-future", now.plus(1, ChronoUnit.HOURS), null));

        Optional<SignatureKeyView> found = keyRegistryPort.lookup(participantId, KeyPurpose.VERIFY, now);

        assertThat(found).isEmpty();
    }

    @Test
    void expiredKeyIsNotFound() {
        Instant now = Instant.now();
        keyRegistryPort.register(new SignatureKeyRegistration(participantId, KeyPurpose.VERIFY, "ED25519-SYNTH",
                "pub-expired", "ref-expired", now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS)));

        Optional<SignatureKeyView> found = keyRegistryPort.lookup(participantId, KeyPurpose.VERIFY, now);

        assertThat(found).isEmpty();
    }

    @Test
    void unknownKeyIsNotFound() {
        Optional<SignatureKeyView> found = keyRegistryPort.lookup(UUID.randomUUID(), KeyPurpose.VERIFY,
                Instant.now());

        assertThat(found).isEmpty();
    }

    @Test
    void keyRegisteredWithBothPurposeSatisfiesEitherLookup() {
        Instant now = Instant.now();
        keyRegistryPort.register(new SignatureKeyRegistration(participantId, KeyPurpose.BOTH, "ED25519-SYNTH",
                "pub-both", "ref-both", now.minus(1, ChronoUnit.HOURS), null));

        assertThat(keyRegistryPort.lookup(participantId, KeyPurpose.VERIFY, now)).isPresent();
        assertThat(keyRegistryPort.lookup(participantId, KeyPurpose.SIGN, now)).isPresent();
    }

    static synchronized void initializeDatabase() {
        if (initialized) {
            return;
        }
        POSTGRES.start();
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot initialize PostgreSQL test container", exception);
        }
        org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
        initialized = true;
    }
}

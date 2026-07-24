package com.sepanexus.modules.paymentlifecycle.isoadapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Upgrade-path proof for {@code V61__iso_messages_source_and_recorded_timestamps.sql}: historical
 * {@code cre_dt_tm} values are copied to {@code recorded_at} only; source creation time is not
 * fabricated from record/receive time.
 */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class IsoMessagesSourceRecordedTimestampsMigrationUpgradeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");

    @Test
    void recordedAtIsBackfilledFromLegacyCreDtTmAndSourceCreationStaysUnknown() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }

        Flyway preUpgrade = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .target("60")
                .load();
        preUpgrade.migrate();
        assertThat(preUpgrade.info().current().getVersion().toString()).isEqualTo("60");

        JdbcTemplate jdbcTemplate = jdbcTemplate();
        UUID isoMessageId = UUID.randomUUID();
        Instant legacyRecordedAt = Instant.parse("2026-07-10T08:30:00Z");
        jdbcTemplate.update("""
                INSERT INTO iso.iso_messages (id, direction, message_type, parse_status, cre_dt_tm)
                VALUES (?, 'INBOUND', 'pain.001', 'PARSED', ?)
                """, isoMessageId, Timestamp.from(legacyRecordedAt));

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT cre_dt_tm, recorded_at, source_message_created_at FROM iso.iso_messages WHERE id = ?",
                isoMessageId);
        assertThat(row.get("cre_dt_tm")).isEqualTo(Timestamp.from(legacyRecordedAt));
        assertThat(row.get("recorded_at")).isEqualTo(Timestamp.from(legacyRecordedAt));
        assertThat(row.get("source_message_created_at"))
                .as("historical source CreDtTm must stay unknown when not re-parsed")
                .isNull();
    }

    private static JdbcTemplate jdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "sepa_app", "dev-only-app");
        return new JdbcTemplate(dataSource);
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}

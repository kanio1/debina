package com.sepanexus.modules.paymentlifecycle.isoadapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
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
 * EPIC-27 Story 27.2C: the real upgrade path for {@code V21__iso_message_correlation.sql} —
 * migrates to V20 (the version immediately before), seeds representative pre-V21 data, then
 * applies V21 and confirms both (a) the {@code tenant_id} backfill actually ran against real old
 * rows and (b) a legacy row with no {@code raw_message_id} (the same shape V17's own backfill
 * produces) is deliberately left {@code NULL}, not fabricated. A fresh-migration test alone never
 * proves this — see {@code sepa-nexus-flyway-safe-change} skill's upgrade-verification guidance.
 */
@Testcontainers
class IsoMessageCorrelationMigrationUpgradeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");

    @Test
    void tenantIdIsBackfilledForPreExistingRowsAndLeftNullWithoutARawMessage() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }

        Flyway preUpgrade = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .target("20")
                .load();
        preUpgrade.migrate();
        assertThat(preUpgrade.info().current().getVersion().toString()).isEqualTo("20");

        JdbcTemplate jdbcTemplate = jdbcTemplate();
        UUID tenantId = UUID.randomUUID();
        UUID rawMessageId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ingress.raw_inbound_messages (id, channel, tenant_id, message_type, payload, payload_sha256)
                VALUES (?, 'bank-xml', ?, 'pain.001', '\\x00', '\\x00')
                """, rawMessageId, tenantId);

        UUID isoMessageWithRawMessage = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO iso.iso_messages (id, direction, message_type, parse_status, raw_message_id)
                VALUES (?, 'INBOUND', 'pain.001', 'PARSED', ?)
                """, isoMessageWithRawMessage, rawMessageId);

        UUID isoMessageWithoutRawMessage = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO iso.iso_messages (id, direction, message_type, parse_status)
                VALUES (?, 'INBOUND', 'JSON_DIRECT', 'SKIPPED')
                """, isoMessageWithoutRawMessage);

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();

        Map<String, Object> withRawMessage = jdbcTemplate.queryForMap(
                "SELECT tenant_id FROM iso.iso_messages WHERE id = ?", isoMessageWithRawMessage);
        assertThat(withRawMessage.get("tenant_id")).isEqualTo(tenantId);

        Map<String, Object> withoutRawMessage = jdbcTemplate.queryForMap(
                "SELECT tenant_id FROM iso.iso_messages WHERE id = ?", isoMessageWithoutRawMessage);
        assertThat(withoutRawMessage.get("tenant_id"))
                .as("a legacy row with no raw_message_id must stay NULL, never fabricated")
                .isNull();

        List<CorrelationCandidate> candidatesForTenant = new JdbcCorrelationCandidateLookup(jdbcTemplate)
                .findByOrgnlMsgIdAndOrgnlEndToEndId(tenantId, "MSG-UPGRADE", "E2E-UPGRADE");
        assertThat(candidatesForTenant).isEmpty();

        UUID candidatePaymentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO iso.payment_iso_identifiers
                    (payment_id, source_message_type, iso_message_id, msg_id, end_to_end_id)
                VALUES (?, 'pain.001', ?, 'MSG-UPGRADE', 'E2E-UPGRADE')
                """, candidatePaymentId, isoMessageWithRawMessage);
        List<CorrelationCandidate> afterInsert = new JdbcCorrelationCandidateLookup(jdbcTemplate)
                .findByOrgnlMsgIdAndOrgnlEndToEndId(tenantId, "MSG-UPGRADE", "E2E-UPGRADE");
        assertThat(afterInsert).extracting(CorrelationCandidate::paymentId).containsExactly(candidatePaymentId);
    }

    private static JdbcTemplate jdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "sepa_app", "dev-only-app");
        return new JdbcTemplate(dataSource);
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}

package com.sepanexus.egress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * EPIC-18 Story 18.2: {@code outbox_dispatcher_role} (ADR-N5, shared-kernel) gets a narrow
 * {@code SELECT}/{@code UPDATE(published_at)} grant on every currently-existing
 * {@code <schema>.outbox_events} table — never {@code INSERT}/{@code DELETE}/{@code TRUNCATE}, and
 * never any grant at all on a domain table. Discovery of "every currently-existing outbox" is
 * dynamic (a live {@code information_schema} query against a fresh migration), not a hardcoded
 * allowlist, so this test automatically covers future outboxes once {@code EPIC-18} Story 18.1
 * adds them.
 */
@Testcontainers
class OutboxDispatcherNoDomainWriteSweepTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin")
            .withStartupAttempts(3);

    @BeforeAll
    static void migrateFreshDatabase() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();
    }

    static List<String> discoverOutboxSchemas() throws Exception {
        List<String> schemas = new ArrayList<>();
        try (Connection connection = adminConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("""
                        SELECT table_schema FROM information_schema.tables
                        WHERE table_name = 'outbox_events'
                        ORDER BY table_schema
                        """)) {
            while (result.next()) {
                schemas.add(result.getString(1));
            }
        }
        return schemas;
    }

    @ParameterizedTest
    @MethodSource("discoverOutboxSchemas")
    void dispatcherCanSelectAndUpdatePublishedAtOnEveryOutbox(String schema) throws Exception {
        String eventId = insertOutboxRowAsAdmin(schema);
        try (Connection connection = dispatcherConnection(); Statement statement = connection.createStatement()) {
            ResultSet result = statement.executeQuery(
                    "SELECT id FROM %s.outbox_events WHERE id = '%s'".formatted(schema, eventId));
            assertThat(result.next()).isTrue();

            int updated = statement.executeUpdate(
                    "UPDATE %s.outbox_events SET published_at = now() WHERE id = '%s'".formatted(schema, eventId));
            assertThat(updated).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @MethodSource("discoverOutboxSchemas")
    void dispatcherCannotInsertIntoAnyOutbox(String schema) {
        assertThatThrownBy(() -> {
            try (Connection connection = dispatcherConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate(insertOutboxSql(schema));
            }
        }).isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
    }

    @ParameterizedTest
    @MethodSource("discoverOutboxSchemas")
    void dispatcherCannotUpdateNonPublishedAtColumnOnAnyOutbox(String schema) throws Exception {
        String eventId = insertOutboxRowAsAdmin(schema);
        assertThatThrownBy(() -> {
            try (Connection connection = dispatcherConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "UPDATE %s.outbox_events SET payload = '{}'::jsonb WHERE id = '%s'".formatted(schema, eventId));
            }
        }).isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
    }

    @ParameterizedTest
    @MethodSource("discoverOutboxSchemas")
    void dispatcherCannotDeleteFromAnyOutbox(String schema) throws Exception {
        String eventId = insertOutboxRowAsAdmin(schema);
        assertThatThrownBy(() -> {
            try (Connection connection = dispatcherConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM %s.outbox_events WHERE id = '%s'".formatted(schema, eventId));
            }
        }).isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
    }

    @ParameterizedTest
    @MethodSource("discoverOutboxSchemas")
    void dispatcherCannotTruncateAnyOutbox(String schema) {
        assertThatThrownBy(() -> {
            try (Connection connection = dispatcherConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("TRUNCATE %s.outbox_events".formatted(schema));
            }
        }).isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
    }

    @Test
    void dispatcherHasNoWritePrivilegeOnAnyNonOutboxTable() throws Exception {
        List<String> violations = new ArrayList<>();
        try (Connection connection = adminConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("""
                        SELECT table_schema, table_name FROM information_schema.tables
                        WHERE table_type = 'BASE TABLE'
                          AND table_schema NOT IN ('pg_catalog', 'information_schema')
                          AND table_name NOT IN ('outbox_events', 'flyway_schema_history')
                        ORDER BY table_schema, table_name
                        """)) {
            while (result.next()) {
                String schema = result.getString(1);
                String table = result.getString(2);
                String qualified = schema + "." + table;
                try (PreparedStatement check = connection.prepareStatement("""
                        SELECT
                          has_table_privilege('outbox_dispatcher_role', ?, 'INSERT') AS ins,
                          has_table_privilege('outbox_dispatcher_role', ?, 'UPDATE') AS upd,
                          has_table_privilege('outbox_dispatcher_role', ?, 'DELETE') AS del,
                          has_table_privilege('outbox_dispatcher_role', ?, 'TRUNCATE') AS trunc
                        """)) {
                    check.setString(1, qualified);
                    check.setString(2, qualified);
                    check.setString(3, qualified);
                    check.setString(4, qualified);
                    ResultSet privileges = check.executeQuery();
                    privileges.next();
                    if (privileges.getBoolean("ins") || privileges.getBoolean("upd")
                            || privileges.getBoolean("del") || privileges.getBoolean("trunc")) {
                        violations.add(qualified);
                    }
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void dispatcherCannotReallyInsertIntoPaymentPayments() {
        assertDenied(() -> """
                INSERT INTO payment.payments (id, tenant_id, amount, currency, debtor_iban, creditor_iban, status)
                VALUES (gen_random_uuid(), gen_random_uuid(), 10.00, 'EUR', 'DE1', 'FR1', 'RECEIVED')
                """);
    }

    @Test
    void dispatcherCannotReallyInsertIntoIsoMessages() {
        assertDenied(() -> """
                INSERT INTO iso.iso_messages (id, direction, message_type, parse_status)
                VALUES (gen_random_uuid(), 'INBOUND', 'pain.001', 'PARSED')
                """);
    }

    @Test
    void dispatcherCannotReallyInsertIntoEgressOutboundMessages() {
        assertDenied(() -> """
                INSERT INTO egress.outbound_messages
                    (tenant_id, recipient_id, channel, artifact_type, correlation_msg_id, payload, payload_sha256)
                VALUES (gen_random_uuid(), gen_random_uuid(), 'WEBHOOK', 'PACS002', 'corr-1', 'x'::bytea, sha256('x'::bytea))
                """);
    }

    @Test
    void dispatcherCannotReallyInsertIntoSignatureKeys() {
        assertDenied(() -> """
                INSERT INTO signature.signature_keys (id, purpose, algo, public_material, valid_from)
                VALUES (gen_random_uuid(), 'VERIFY', 'Ed25519', 'x', now())
                """);
    }

    @Test
    void dispatcherCannotReallyInsertIntoLedgerJournalEntries() {
        assertDenied(() -> """
                INSERT INTO ledger.journal_entries (id, entry_type, business_date)
                VALUES (gen_random_uuid(), 'POST', CURRENT_DATE)
                """);
    }

    @Test
    void dispatcherCannotReallyInsertIntoReferenceDataSchemeProfiles() {
        assertDenied(() -> """
                INSERT INTO reference_data.scheme_profiles (id, profile_code, family, valid_from)
                VALUES (gen_random_uuid(), 'TEST_LIKE', 'GROSS_INSTANT', CURRENT_DATE)
                """);
    }

    private void assertDenied(java.util.function.Supplier<String> sql) {
        assertThatThrownBy(() -> {
            try (Connection connection = dispatcherConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql.get());
            }
        }).isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
    }

    private static String insertOutboxRowAsAdmin(String schema) throws Exception {
        String eventId;
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            ResultSet result = statement.executeQuery(
                    "INSERT INTO %s.outbox_events (id, aggregate_id, %s, payload) VALUES (gen_random_uuid(), gen_random_uuid(), %s, '{}'::jsonb) RETURNING id"
                            .formatted(schema, typeColumns(schema), typeValues(schema)));
            result.next();
            eventId = result.getString(1);
        }
        return eventId;
    }

    private static String insertOutboxSql(String schema) {
        return "INSERT INTO %s.outbox_events (aggregate_id, %s, payload) VALUES (gen_random_uuid(), %s, '{}'::jsonb)"
                .formatted(schema, typeColumns(schema), typeValues(schema));
    }

    /**
     * The outbox_events table shape differs slightly per schema (egress uses {@code topic, type};
     * payment/iso use {@code event_type, correlation_id}) — both are pre-existing, source-defined
     * shapes from prior stories, not something this story is free to normalize.
     */
    private static String typeColumns(String schema) {
        return "egress".equals(schema) ? "topic, type" : "event_type, correlation_id";
    }

    private static String typeValues(String schema) {
        return "egress".equals(schema)
                ? "'sweep.test', 'sweep.test.v1'"
                : "'sweep.test.v1', gen_random_uuid()";
    }

    private static Connection dispatcherConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "outbox_dispatcher_role", "dev-only-outbox-dispatcher");
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}

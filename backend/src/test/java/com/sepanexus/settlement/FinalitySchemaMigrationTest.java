package com.sepanexus.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Fresh PostgreSQL 18 proof of ADR-N10's finality catalog, authority and writer boundaries. */
@Testcontainers
class FinalitySchemaMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");

    @BeforeAll
    static void migrate() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
    }

    @Test
    void freshMigrationCreatesTheApprovedVersionedCatalogAndProjectionColumns() throws Exception {
        assertThat(strings("""
                SELECT finality_rule_code || ':' || finality_rule_version
                FROM reference_data.finality_rules ORDER BY finality_rule_code
                """)).containsExactly(
                        "ON_CYCLE_SETTLED:1", "ON_INTERNAL_BOOK_POST:1", "ON_LEDGER_POST:1", "ON_NET_POSITION_SETTLED:1");
        assertThat(strings("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'payment' AND table_name = 'payments'
                    AND column_name IN ('finality_at', 'finality_record_id') ORDER BY column_name
                """)).containsExactly("finality_at", "finality_record_id");
    }

    @Test
    void authorityTablesAreAppendOnlyAndTheirUniqueEvidenceKeysFailClosed() throws Exception {
        UUID attempt = UUID.randomUUID();
        UUID snapshot = UUID.randomUUID();
        UUID record = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        try (Connection connection = settlement(); PreparedStatement insertSnapshot = connection.prepareStatement("""
                INSERT INTO settlement.settlement_profile_snapshots
                    (id, settlement_attempt_id, finality_rule_code, finality_rule_version)
                VALUES (?, ?, 'ON_LEDGER_POST', 1)
                """)) {
            insertSnapshot.setObject(1, snapshot); insertSnapshot.setObject(2, attempt); insertSnapshot.executeUpdate();
        }
        try (Connection connection = settlement(); PreparedStatement insertRecord = connection.prepareStatement("""
                INSERT INTO settlement.settlement_finality_records
                    (id, payment_id, settlement_attempt_id, profile_snapshot_id, finality_rule_code,
                     finality_rule_version, source_type, source_id, source_occurred_at, finality_at, evidence_hash)
                VALUES (?, ?, ?, ?, 'ON_LEDGER_POST', 1, 'LEDGER_ENTRY', ?, ?, ?, ?)
                """)) {
            insertRecord.setObject(1, record); insertRecord.setObject(2, payment); insertRecord.setObject(3, attempt);
            insertRecord.setObject(4, snapshot); insertRecord.setObject(5, source);
            insertRecord.setObject(6, java.sql.Timestamp.from(Instant.parse("2026-07-20T10:15:30.123Z")));
            insertRecord.setObject(7, java.sql.Timestamp.from(Instant.parse("2026-07-20T10:15:30.123Z")));
            insertRecord.setBytes(8, new byte[] {1}); insertRecord.executeUpdate();
        }
        assertThatThrownBy(() -> sqlAsAdmin("UPDATE settlement.settlement_profile_snapshots SET finality_rule_version = 1 WHERE id = '" + snapshot + "'"))
                .isInstanceOf(SQLException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> sqlAsAdmin("DELETE FROM settlement.settlement_finality_records WHERE id = '" + record + "'"))
                .isInstanceOf(SQLException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> sqlAsAdmin("""
                INSERT INTO settlement.settlement_finality_records
                    (payment_id, settlement_attempt_id, profile_snapshot_id, finality_rule_code,
                     finality_rule_version, source_type, source_id, source_occurred_at, finality_at, evidence_hash)
                VALUES (gen_random_uuid(), '%s', '%s', 'ON_LEDGER_POST', 1, 'LEDGER_ENTRY', '%s',
                        '2026-07-20T10:15:30.123Z', '2026-07-20T10:15:30.123Z', '\\x02')
                """.formatted(attempt, snapshot, source)))
                .isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "23505");
    }

    @Test
    void settlementCanWriteOnlyItsAuthorityTablesAndCannotWritePaymentProjection() throws Exception {
        assertThat(booleanValue("SELECT has_schema_privilege('settlement_role', 'settlement', 'USAGE')")).isTrue();
        assertThat(booleanValue("SELECT has_table_privilege('settlement_role', 'settlement.settlement_finality_records', 'INSERT')")).isTrue();
        assertThat(booleanValue("SELECT has_schema_privilege('settlement_role', 'payment', 'USAGE')")).isFalse();
        assertThat(booleanValue("SELECT has_table_privilege('settlement_role', 'payment.payments', 'UPDATE')")).isFalse();
        assertThatThrownBy(() -> {
            try (Connection connection = settlement(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE payment.payments SET finality_at = now()");
            }
        }).isInstanceOf(SQLException.class).hasFieldOrPropertyWithValue("SQLState", "42501");
    }

    private static List<String> strings(String sql) throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            java.util.ArrayList<String> values = new java.util.ArrayList<>();
            while (result.next()) values.add(result.getString(1));
            return values;
        }
    }

    private static boolean booleanValue(String sql) throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next(); return result.getBoolean(1);
        }
    }

    private static void sqlAsAdmin(String sql) throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) { statement.executeUpdate(sql); }
    }

    private static Connection settlement() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "settlement_role", "dev-only-settlement");
    }

    private static Connection admin() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}

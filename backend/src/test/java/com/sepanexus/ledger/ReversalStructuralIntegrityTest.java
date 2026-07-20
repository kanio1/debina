package com.sepanexus.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Structural only: this does not claim a runtime reversal or finality decision. */
@Testcontainers
class ReversalStructuralIntegrityTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");
    @BeforeAll static void migrate() throws Exception {
        try (var c = admin(); var s = c.createStatement()) { s.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'"); }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration").locations("filesystem:src/main/resources/db/migration").load().migrate();
    }
    @Test void rejectsReversalWithoutOriginalPointer() { assertRejected("REVERSAL", null); }
    @Test void rejectsNonReversalWithPointer() throws Exception { assertRejected("POST", insert("POST", null)); }
    @Test void rejectsSelfReversal() { UUID id = UUID.randomUUID(); assertRejected("REVERSAL", id, id); }
    @Test void permitsFirstReversalButRejectsSecondForSameOriginal() throws Exception {
        UUID original = insert("POST", null); UUID first = insert("REVERSAL", original);
        assertThat(first).isNotNull(); assertRejected("REVERSAL", original);
    }
    @Test void reversalDoesNotModifyOriginalLines() throws Exception {
        UUID original = insert("POST", null); UUID account = account();
        try (var c = admin(); var s = c.createStatement()) { c.setAutoCommit(false); s.executeUpdate("INSERT INTO ledger.journal_lines (entry_id,line_no,account_id,currency,amount_minor,at) VALUES ('%s',1,'%s','EUR',0,now())".formatted(original, account)); c.commit(); }
        insert("REVERSAL", original);
        try (var c = admin(); var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM ledger.journal_lines WHERE entry_id = '%s'".formatted(original))) { r.next(); assertThat(r.getInt(1)).isEqualTo(1); }
    }
    private static void assertRejected(String type, UUID pointer) { assertRejected(type, pointer, UUID.randomUUID()); }
    private static void assertRejected(String type, UUID pointer, UUID id) { SQLException e = assertThrows(SQLException.class, () -> insert(id, type, pointer)); assertThat(e.getSQLState()).isIn("23514", "23505"); }
    private static UUID insert(String type, UUID pointer) throws Exception { return insert(UUID.randomUUID(), type, pointer); }
    private static UUID insert(UUID id, String type, UUID pointer) throws Exception { try (var c = admin(); var s = c.createStatement()) { s.executeUpdate("INSERT INTO ledger.journal_entries (id,entry_type,business_date,reversal_of_entry_id) VALUES ('%s','%s',CURRENT_DATE,%s)".formatted(id,type,pointer == null ? "NULL" : "'" + pointer + "'")); } return id; }
    private static UUID account() throws Exception { UUID id = UUID.randomUUID(); try (var c = admin(); var s = c.createStatement()) { s.executeUpdate("INSERT INTO ledger.liquidity_accounts (id,tenant_id,participant_id,currency,available_minor) VALUES ('%s',gen_random_uuid(),gen_random_uuid(),'EUR',0)".formatted(id)); } return id; }
    private static Connection admin() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
}

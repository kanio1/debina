package com.sepanexus.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Isolated V28→V29 upgrade proof with representative valid ledger evidence. */
@Testcontainers
class LedgerIntegrityMigrationUpgradePathTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");
    @Test void preservesValidLedgerDataAndActivatesNewConstraints() throws Exception {
        try (var c = admin(); var s = c.createStatement()) { s.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'"); }
        migrate("28");
        UUID account = UUID.randomUUID(); UUID entry = UUID.randomUUID();
        try (var c = admin(); var s = c.createStatement()) {
            c.setAutoCommit(false);
            s.executeUpdate("INSERT INTO ledger.liquidity_accounts (id,tenant_id,participant_id,currency,available_minor) VALUES ('%s',gen_random_uuid(),gen_random_uuid(),'EUR',0)".formatted(account));
            s.executeUpdate("INSERT INTO ledger.journal_entries (id,entry_type,business_date) VALUES ('%s','POST',CURRENT_DATE)".formatted(entry));
            s.executeUpdate("INSERT INTO ledger.journal_lines (entry_id,line_no,account_id,currency,amount_minor,at) VALUES ('%s',1,'%s','EUR',0,now())".formatted(entry,account)); c.commit();
        }
        migrate(null);
        try (var c = admin(); var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM ledger.journal_lines WHERE entry_id='%s'".formatted(entry))) { r.next(); assertThat(r.getInt(1)).isEqualTo(1); }
        SQLException error = assertThrows(SQLException.class, () -> { try (var c = admin(); var s = c.createStatement()) { s.executeUpdate("INSERT INTO ledger.journal_lines (entry_id,line_no,account_id,currency,amount_minor,at) VALUES ('%s',2,gen_random_uuid(),'EUR',0,now())".formatted(entry)); } });
        assertThat(error.getSQLState()).isEqualTo("23503");
    }
    private static void migrate(String target) { var c = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration").locations("filesystem:src/main/resources/db/migration"); if (target != null) c.target(target); c.load().migrate(); }
    private static Connection admin() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
}

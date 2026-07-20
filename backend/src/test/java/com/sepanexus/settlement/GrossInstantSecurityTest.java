package com.sepanexus.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL 18 security proof for the ADR-N11 function-owner/executor boundary. */
@Testcontainers
class GrossInstantSecurityTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");

    @BeforeAll
    static void migrate() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
            statement.execute("CREATE ROLE gross_instant_untrusted LOGIN PASSWORD 'dev-only-untrusted'");
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
    }

    @Test
    void executorHasOnlyCommandExecution_notDomainTableDmlOrOwnerMembership() throws Exception {
        assertThat(bool("SELECT has_table_privilege('gross_instant_executor_role', 'ledger.journal_entries', 'INSERT')")).isFalse();
        assertThat(bool("SELECT has_table_privilege('gross_instant_executor_role', 'settlement.settlement_finality_records', 'INSERT')")).isFalse();
        assertThat(bool("SELECT has_table_privilege('gross_instant_executor_role', 'payment.payments', 'UPDATE')")).isFalse();
        assertThat(count("""
                SELECT count(*) FROM pg_auth_members member
                JOIN pg_roles role ON role.oid = member.roleid
                JOIN pg_roles grantee ON grantee.oid = member.member
                WHERE grantee.rolname = 'gross_instant_executor_role'
                  AND role.rolname LIKE '%_gross_instant_function_owner'
                """)).isZero();
        assertThat(functionExecute("gross_instant_executor_role")).containsExactly(
                "ledger.gross_instant_reserve_post", "payment.project_gross_instant_finality",
                "payment.record_gross_instant_insufficient_liquidity", "settlement.record_gross_instant_insufficient_liquidity",
                "settlement.record_gross_instant_post");
        assertThatThrownBy(() -> executeAsExecutor("INSERT INTO ledger.journal_entries (entry_type, business_date) VALUES ('POST', CURRENT_DATE)"))
                .isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
    }

    @Test
    void functionSecurityContractIsExact_andPublicCannotExecute() throws Exception {
        assertThat(rows("""
                SELECT n.nspname || '.' || p.proname || ':' || p.prosecdef || ':' || p.provolatile::text || ':' || p.proparallel::text || ':' ||
                    array_to_string(p.proconfig, ',')
                FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE (n.nspname, p.proname) IN (('ledger','gross_instant_reserve_post'),
                    ('settlement','record_gross_instant_post'), ('settlement','record_gross_instant_insufficient_liquidity'),
                    ('payment','project_gross_instant_finality'), ('payment','record_gross_instant_insufficient_liquidity'))
                ORDER BY 1
                """)).allSatisfy(contract -> {
                    assertThat(contract).contains(":true:v:u:");
                    assertThat(contract).contains("search_path=pg_catalog,");
                    assertThat(contract).contains(", pg_temp");
                    assertThat(contract).doesNotContain("public");
                });
        assertThat(count("""
                SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE (n.nspname, p.proname) IN (('ledger','gross_instant_reserve_post'),
                    ('settlement','record_gross_instant_post'), ('settlement','record_gross_instant_insufficient_liquidity'),
                    ('payment','project_gross_instant_finality'), ('payment','record_gross_instant_insufficient_liquidity'))
                  AND has_function_privilege('public', p.oid, 'EXECUTE')
                """)).isZero();
    }

    @Test
    void functionOwnersAreModuleScoped_andNoUntrustedRoleCanCreateInEffectiveSearchPath() throws Exception {
        assertThat(bool("SELECT has_table_privilege('ledger_gross_instant_function_owner', 'payment.payments', 'UPDATE')")).isFalse();
        assertThat(bool("SELECT has_table_privilege('settlement_gross_instant_function_owner', 'ledger.reservations', 'INSERT')")).isFalse();
        assertThat(bool("SELECT has_table_privilege('payment_gross_instant_function_owner', 'settlement.settlement_finality_records', 'INSERT')")).isFalse();
        for (String schema : List.of("ledger", "settlement", "payment", "reference_data")) {
            assertThat(bool("SELECT has_schema_privilege('gross_instant_untrusted', '" + schema + "', 'CREATE')"))
                    .as(schema).isFalse();
            assertThatThrownBy(() -> executeAsUntrusted("CREATE FUNCTION " + schema + ".hostile_" + schema
                    + "() RETURNS integer LANGUAGE sql AS 'SELECT 1'"))
                    .as(schema).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
        }
    }

    @Test
    void hostileFunctionInAnEffectiveSchemaCannotHijackAnExecutedPaymentCommand() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        try (Connection connection = admin(); PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO payment.payments (id, tenant_id, amount, debtor_iban, creditor_iban, status)
                VALUES (?, ?, ?, 'DE89370400440532013000', 'FR7630006000011234567890189', 'VALIDATED')
                """)) {
            insert.setObject(1, payment); insert.setObject(2, tenant); insert.setBigDecimal(3, new BigDecimal("4.00"));
            insert.executeUpdate();
        }
        try {
            try (Connection connection = admin(); Statement statement = connection.createStatement()) {
                statement.execute("GRANT USAGE, CREATE ON SCHEMA payment TO gross_instant_untrusted");
            }
            executeAsUntrusted("""
                    CREATE FUNCTION payment.current_setting(text, boolean) RETURNS text LANGUAGE plpgsql AS $$
                    BEGIN RAISE EXCEPTION 'hostile current_setting was executed'; END;
                    $$
                    """);
            try (Connection connection = executor()) {
                connection.setAutoCommit(false);
                try (PreparedStatement guc = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
                    guc.setString(1, tenant.toString()); guc.execute();
                }
                try (PreparedStatement command = connection.prepareStatement("""
                        SELECT rejection_outcome FROM payment.record_gross_instant_insufficient_liquidity(?, ?, ?, now())
                        """)) {
                    command.setObject(1, tenant); command.setObject(2, payment); command.setObject(3, UUID.randomUUID());
                    try (ResultSet result = command.executeQuery()) {
                        assertThat(result.next()).isTrue(); assertThat(result.getString(1)).isEqualTo("REJECTED");
                    }
                }
                connection.commit();
            }
        } finally {
            try (Connection connection = admin(); Statement statement = connection.createStatement()) {
                statement.execute("DROP FUNCTION IF EXISTS payment.current_setting(text, boolean)");
                statement.execute("REVOKE CREATE, USAGE ON SCHEMA payment FROM gross_instant_untrusted");
            }
        }
    }

    @Test
    void paymentAndSettlementCommandsRejectAnEmptyTenantContext() throws Exception {
        SQLException paymentFailure = org.junit.jupiter.api.Assertions.assertThrows(SQLException.class, () ->
                executeAsExecutor("SELECT * FROM payment.record_gross_instant_insufficient_liquidity(gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), now())"));
        assertThat(paymentFailure.getSQLState()).isEqualTo("22023");
        SQLException settlementFailure = org.junit.jupiter.api.Assertions.assertThrows(SQLException.class, () ->
                executeAsExecutor("SELECT * FROM settlement.record_gross_instant_insufficient_liquidity(gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), 1, 'EUR', now())"));
        assertThat(settlementFailure.getSQLState()).isEqualTo("22023");
    }

    private static List<String> functionExecute(String role) throws Exception {
        return rows("""
                SELECT n.nspname || '.' || p.proname
                FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname IN ('ledger', 'settlement', 'payment')
                  AND has_function_privilege('%s', p.oid, 'EXECUTE')
                  AND p.proname LIKE '%%gross_instant%%'
                ORDER BY 1
                """.formatted(role));
    }
    private static boolean bool(String sql) throws Exception { return Boolean.parseBoolean(value(sql)); }
    private static long count(String sql) throws Exception { return Long.parseLong(value(sql)); }
    private static String value(String sql) throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue(); return result.getString(1);
        }
    }
    private static List<String> rows(String sql) throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            java.util.ArrayList<String> resultRows = new java.util.ArrayList<>(); while (result.next()) resultRows.add(result.getString(1)); return resultRows;
        }
    }
    private static void executeAsExecutor(String sql) throws SQLException { execute("gross_instant_executor_role", "dev-only-gross-instant-executor", sql); }
    private static void executeAsUntrusted(String sql) throws SQLException { execute("gross_instant_untrusted", "dev-only-untrusted", sql); }
    private static void execute(String user, String password, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), user, password); Statement statement = connection.createStatement()) { statement.execute(sql); }
    }
    private static Connection admin() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
    private static Connection executor() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "gross_instant_executor_role", "dev-only-gross-instant-executor"); }
}

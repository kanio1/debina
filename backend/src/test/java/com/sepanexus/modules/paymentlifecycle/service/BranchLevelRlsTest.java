package com.sepanexus.modules.paymentlifecycle.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * EPIC-22 Story 22.1: two-level RLS — {@code app.branch_id} additionally scopes visibility within
 * a tenant, but only when the session actually sets it (sepa-nexus-message-flow-and-data-blueprint.md
 * §4.7). Same pattern as {@link com.sepanexus.payment.PaymentsRlsTest} (direct JDBC as
 * {@code sepa_app}), extended to the branch dimension.
 */
@Testcontainers
class BranchLevelRlsTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_A = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID BRANCH_B = UUID.fromString("00000000-0000-0000-0000-000000000102");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");

    @BeforeAll
    static void migrateDatabase() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
    }

    @BeforeEach
    void seedBranchRows() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE payment.payments");
        }
        insertPayment(BRANCH_A);
        insertPayment(BRANCH_B);
    }

    @Test
    void branchScopedSessionSeesOnlyItsOwnBranch() throws Exception {
        try (Connection connection = applicationConnection()) {
            setTenantAndBranch(connection, TENANT, BRANCH_A);

            assertThat(branchesVisible(connection)).containsExactly(BRANCH_A);
        }
    }

    @Test
    void otherBranchScopedSessionSeesOnlyItsOwnBranch() throws Exception {
        try (Connection connection = applicationConnection()) {
            setTenantAndBranch(connection, TENANT, BRANCH_B);

            assertThat(branchesVisible(connection)).containsExactly(BRANCH_B);
        }
    }

    @Test
    void tenantOnlySessionWithNoBranchSeesBothBranches() throws Exception {
        try (Connection connection = applicationConnection()) {
            setTenantOnly(connection, TENANT);

            assertThat(branchesVisible(connection)).containsExactlyInAnyOrder(BRANCH_A, BRANCH_B);
        }
    }

    private static java.util.List<UUID> branchesVisible(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT branch_id FROM payment.payments ORDER BY branch_id");
             ResultSet resultSet = statement.executeQuery()) {
            java.util.List<UUID> rows = new java.util.ArrayList<>();
            while (resultSet.next()) {
                rows.add((UUID) resultSet.getObject(1));
            }
            return rows;
        }
    }

    private static Connection adminConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }

    private static Connection applicationConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "sepa_app", "dev-only-app");
    }

    private static void insertPayment(UUID branchId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment.payments (tenant_id, branch_id, amount, debtor_iban, creditor_iban)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, TENANT);
            statement.setObject(2, branchId);
            statement.setBigDecimal(3, new BigDecimal("10.00"));
            statement.setString(4, "DE89370400440532013000");
            statement.setString(5, "FR7630006000011234567890189");
            statement.executeUpdate();
        }
    }

    private static void setTenantAndBranch(Connection connection, UUID tenantId, UUID branchId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, false), set_config('app.branch_id', ?, false)")) {
            statement.setString(1, tenantId.toString());
            statement.setString(2, branchId.toString());
            statement.execute();
        }
    }

    private static void setTenantOnly(Connection connection, UUID tenantId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, false)")) {
            statement.setString(1, tenantId.toString());
            statement.execute();
        }
    }
}

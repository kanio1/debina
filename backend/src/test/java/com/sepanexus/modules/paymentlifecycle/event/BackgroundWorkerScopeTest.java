package com.sepanexus.modules.paymentlifecycle.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * EPIC-22 Story 22.2 (G3, §4.7): {@code OutboxDispatcher} — the only background worker in this
 * codebase today — touches only queue tables (payment.outbox_events/inbox_events), never
 * payment.payments. Per the frozen decision, queue tables get NO RLS at all (ownership/grants are
 * the boundary instead); this test confirms that decision actually holds in the schema, not just
 * in a design doc. The complementary guarantee — an RLS-protected table sees zero rows on an
 * empty/default session — is already proven generically by
 * {@link com.sepanexus.payment.PaymentsRlsTest#emptyTenantGucReturnsZeroRows()}; not duplicated
 * here.
 */
@org.junit.jupiter.api.Tag("testcontainers")
class BackgroundWorkerScopeTest extends KafkaIntegrationSupport {

    @org.junit.jupiter.api.Test
    void queueTablesHaveNoRowLevelSecurityEnabled() throws Exception {
        assertThat(rowSecurityEnabled("outbox_events")).isFalse();
        assertThat(rowSecurityEnabled("inbox_events")).isFalse();
    }

    @org.junit.jupiter.api.Test
    void paymentsTableStillHasRowLevelSecurityEnabled() throws Exception {
        assertThat(rowSecurityEnabled("payments")).isTrue();
    }

    private static boolean rowSecurityEnabled(String tableName) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT relrowsecurity FROM pg_class
                WHERE relname = ? AND relnamespace = 'payment'::regnamespace
                """)) {
            statement.setString(1, tableName);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }
}

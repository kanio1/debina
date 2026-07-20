package com.sepanexus.modules.paymentlifecycle.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * TEST-GAP-002 runtime proof: the actual scheduled entrypoint uses the restricted relay role,
 * preserves an unpublished row on a permission failure, exposes it, then recovers by publishing.
 */
@SpringBootTest(properties = {
        "sepa.scheduling.enabled=true",
        "sepa.scheduling.relay-fixed-delay-ms=100"
})
class ScheduledRelayOperationalRuntimeTest extends KafkaIntegrationSupport {

    @Autowired
    private OutboxRelayHealthIndicator health;

    @Test
    void permissionFailureIsVisibleAndLaterScheduledSuccessPublishesTheSameEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        try (Connection connection = adminConnection(); var statement = connection.createStatement()) {
            statement.execute("REVOKE SELECT ON payment.outbox_events FROM outbox_dispatcher_role");
        }
        try {
            insertUnpublishedEvent(eventId);

            eventually(() -> {
                assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
                assertThat(health.health().getDetails()).containsEntry("lastFailureCategory", "DATABASE_PERMISSION");
            });
            assertThat(isPublished(eventId)).isFalse();
        } finally {
            try (Connection connection = adminConnection(); var statement = connection.createStatement()) {
                statement.execute("GRANT SELECT, UPDATE (published_at) ON payment.outbox_events TO outbox_dispatcher_role");
            }
        }

        eventually(() -> {
            assertThat(health.health().getStatus()).isEqualTo(Status.UP);
            assertThat(isPublished(eventId)).isTrue();
        });
    }

    private static void insertUnpublishedEvent(UUID eventId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment.outbox_events (id, aggregate_id, event_type, payload, correlation_id)
                VALUES (?, ?, 'payment.status.reported.v1', ?::jsonb, ?)
                """)) {
            statement.setObject(1, eventId);
            statement.setObject(2, UUID.randomUUID());
            statement.setString(3, "{\"source\":\"ScheduledRelayOperationalRuntimeTest\"}");
            statement.setObject(4, UUID.randomUUID());
            statement.executeUpdate();
        }
    }

    private static boolean isPublished(UUID eventId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT published_at IS NOT NULL FROM payment.outbox_events WHERE id = ?
                """)) {
            statement.setObject(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }
}

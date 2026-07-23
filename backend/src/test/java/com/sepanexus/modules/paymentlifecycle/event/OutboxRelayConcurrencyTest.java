package com.sepanexus.modules.paymentlifecycle.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("testcontainers")
class OutboxRelayConcurrencyTest extends KafkaIntegrationSupport {

    @Test
    void twoRelayConnectionsClaimDisjointRowsWithSkipLocked() throws Exception {
        UUID first = insertOutboxRow();
        UUID second = insertOutboxRow();
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondLocked = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<UUID> firstClaim = executor.submit(() -> claimFirst(firstLocked, secondLocked));
            Future<UUID> secondClaim = executor.submit(() -> claimSecond(firstLocked, secondLocked));

            UUID firstClaimed = firstClaim.get(5, TimeUnit.SECONDS);
            UUID secondClaimed = secondClaim.get(5, TimeUnit.SECONDS);
            assertThat(firstClaimed).isIn(first, second);
            assertThat(secondClaimed).isIn(first, second);
            assertThat(firstClaimed).isNotEqualTo(secondClaimed);
        }
    }

    private static UUID claimFirst(CountDownLatch firstLocked, CountDownLatch secondLocked) throws Exception {
        try (Connection connection = relayConnection()) {
            connection.setAutoCommit(false);
            UUID claimed = claimOne(connection);
            firstLocked.countDown();
            assertThat(secondLocked.await(5, TimeUnit.SECONDS)).isTrue();
            connection.rollback();
            return claimed;
        }
    }

    private static UUID claimSecond(CountDownLatch firstLocked, CountDownLatch secondLocked) throws Exception {
        assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
        try (Connection connection = relayConnection()) {
            connection.setAutoCommit(false);
            UUID claimed = claimOne(connection);
            secondLocked.countDown();
            connection.rollback();
            return claimed;
        }
    }

    private static UUID claimOne(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id
                FROM payment.outbox_events
                WHERE published_at IS NULL
                ORDER BY created_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
                """)) {
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getObject(1, UUID.class);
            }
        }
    }

    private static UUID insertOutboxRow() throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment.outbox_events (id, aggregate_id, event_type, payload, correlation_id)
                VALUES (?, ?, 'payment.received.v1', CAST(? AS jsonb), ?)
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, UUID.randomUUID());
            statement.setString(3, "{\"eventId\":\"%s\"}".formatted(id));
            statement.setObject(4, UUID.randomUUID());
            statement.executeUpdate();
        }
        return id;
    }

    private static Connection relayConnection() throws Exception {
        return java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), "outbox_dispatcher_role", "dev-only-outbox-dispatcher");
    }
}

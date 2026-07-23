package com.sepanexus.modules.paymentlifecycle.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sepanexus.modules.paymentlifecycle.isoadapter.IsoOutboxDispatcher;
import com.sepanexus.modules.paymentlifecycle.isoadapter.IsoOutboxEventType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@org.junit.jupiter.api.Tag("testcontainers")
class OutboxRelayRuntimeWiringTest extends KafkaIntegrationSupport {

    @Autowired
    @Qualifier("outboxRelayJdbcTemplate")
    private JdbcTemplate relayJdbcTemplate;

    @Autowired
    private DataSource domainDataSource;

    @Autowired
    @Qualifier("transactionManager")
    private PlatformTransactionManager domainTransactionManager;

    @Autowired
    @Qualifier("outboxRelayTransactionManager")
    private PlatformTransactionManager relayTransactionManager;

    @Autowired
    private OutboxDispatcher paymentDispatcher;

    @Autowired
    private IsoOutboxDispatcher isoDispatcher;

    @Test
    void relayUsesDedicatedRoleToPublishPaymentAndIsoRowsButCannotWriteDomainRows() throws Exception {
        UUID paymentEventId = UUID.randomUUID();
        UUID isoEventId = UUID.randomUUID();
        insertPaymentOutbox(paymentEventId);
        insertIsoOutbox(isoEventId);

        assertThat(relayJdbcTemplate.queryForObject("SELECT current_user", String.class))
                .isEqualTo("outbox_dispatcher_role");
        assertThat(new JdbcTemplate(domainDataSource).queryForObject("SELECT current_user", String.class))
                .isEqualTo("sepa_app");
        assertThat(domainTransactionManager).isNotSameAs(relayTransactionManager);
        assertThat(ReflectionTestUtils.getField(paymentDispatcher, "relayJdbcTemplate")).isSameAs(relayJdbcTemplate);
        assertThat(ReflectionTestUtils.getField(isoDispatcher, "relayJdbcTemplate")).isSameAs(relayJdbcTemplate);

        paymentDispatcher.dispatch();
        isoDispatcher.dispatch();

        eventually(() -> {
            assertThat(publishedAt("payment", paymentEventId)).isNotNull();
            assertThat(publishedAt("iso", isoEventId)).isNotNull();
        });

        assertThatThrownBy(() -> relayJdbcTemplate.update("""
                INSERT INTO payment.outbox_events (id, aggregate_id, event_type, payload, correlation_id)
                VALUES (?, ?, 'payment.received.v1', CAST('{\"eventId\":\"forbidden\"}' AS jsonb), ?)
                """, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
    }

    private static void insertPaymentOutbox(UUID eventId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment.outbox_events (id, aggregate_id, event_type, payload, correlation_id)
                VALUES (?, ?, 'payment.received.v1', CAST(? AS jsonb), ?)
                """)) {
            statement.setObject(1, eventId);
            statement.setObject(2, UUID.randomUUID());
            statement.setString(3, "{\"eventId\":\"%s\"}".formatted(eventId));
            statement.setObject(4, UUID.randomUUID());
            statement.executeUpdate();
        }
    }

    private static void insertIsoOutbox(UUID eventId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO iso.outbox_events (id, aggregate_id, event_type, payload, correlation_id)
                VALUES (?, ?, ?, CAST(? AS jsonb), ?)
                """)) {
            statement.setObject(1, eventId);
            statement.setObject(2, UUID.randomUUID());
            statement.setString(3, IsoOutboxEventType.MESSAGE_CORRELATED.eventType());
            statement.setString(4, "{\"eventId\":\"%s\"}".formatted(eventId));
            statement.setObject(5, UUID.randomUUID());
            statement.executeUpdate();
        }
    }

    private static Object publishedAt(String schema, UUID eventId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT published_at FROM " + schema + ".outbox_events WHERE id = ?")) {
            statement.setObject(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getObject(1);
            }
        }
    }
}

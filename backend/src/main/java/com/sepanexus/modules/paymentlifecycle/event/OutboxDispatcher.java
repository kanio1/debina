package com.sepanexus.modules.paymentlifecycle.event;

import com.sepanexus.shared.ClockPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxDispatcher {

    private final JdbcTemplate relayJdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ClockPort clockPort;
    private final Runnable afterAcknowledgementBeforeMark;

    @Autowired
    public OutboxDispatcher(@Qualifier("outboxRelayJdbcTemplate") JdbcTemplate relayJdbcTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            ClockPort clockPort) {
        this(relayJdbcTemplate, kafkaTemplate, clockPort, () -> { });
    }

    OutboxDispatcher(JdbcTemplate relayJdbcTemplate, KafkaTemplate<String, String> kafkaTemplate,
            ClockPort clockPort, Runnable afterAcknowledgementBeforeMark) {
        this.relayJdbcTemplate = relayJdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.clockPort = clockPort;
        this.afterAcknowledgementBeforeMark = afterAcknowledgementBeforeMark;
    }

    /** A caller controls scheduling; this method is one atomic claim/publish/mark batch. */
    @Transactional(transactionManager = "outboxRelayTransactionManager")
    public void dispatch() {
        List<PaymentOutboxRow> unpublished = relayJdbcTemplate.query("""
                SELECT id, aggregate_id, event_type, payload, created_at
                FROM payment.outbox_events
                WHERE published_at IS NULL
                ORDER BY created_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT 50
                """, this::mapRow);
        for (PaymentOutboxRow event : unpublished) {
            publish(event);
            relayJdbcTemplate.update("""
                    UPDATE payment.outbox_events
                    SET published_at = ?
                    WHERE id = ?
                      AND published_at IS NULL
                    """, Timestamp.from(clockPort.now()), event.id());
        }
    }

    private void publish(PaymentOutboxRow event) {
        try {
            PaymentOutboxEventType eventType = PaymentOutboxEventType.fromEventType(event.eventType())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unrecognized payment outbox event type " + event.eventType() + " for event " + event.id()));
            kafkaTemplate.send(eventType.topic(), event.aggregateId().toString(), event.payload())
                    .get(5, TimeUnit.SECONDS);
            afterAcknowledgementBeforeMark.run();
        } catch (Exception exception) {
            throw new OutboxRelayPublicationException("payment", event.id(), exception);
        }
    }

    private PaymentOutboxRow mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new PaymentOutboxRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("aggregate_id", UUID.class),
                resultSet.getString("event_type"),
                resultSet.getString("payload"));
    }

    private record PaymentOutboxRow(UUID id, UUID aggregateId, String eventType, String payload) {
    }
}

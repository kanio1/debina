package com.sepanexus.modules.paymentlifecycle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events", schema = "payment")
public class OutboxEvent {

    public static final String PAYMENT_SUBMITTED = "payment.submitted.v1";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private String payload;

    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    protected OutboxEvent() {
    }

    public static OutboxEvent paymentSubmitted(UUID aggregateId, String payload, UUID correlationId) {
        OutboxEvent event = new OutboxEvent();
        event.aggregateId = aggregateId;
        event.eventType = PAYMENT_SUBMITTED;
        event.payload = payload;
        event.correlationId = correlationId;
        event.createdAt = OffsetDateTime.now();
        return event;
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }

    public void markPublished() {
        publishedAt = OffsetDateTime.now();
    }
}

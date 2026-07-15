package com.sepanexus.modules.paymentlifecycle.event;

import com.sepanexus.modules.paymentlifecycle.domain.PaymentLifecycleEvent;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentStatus;
import com.sepanexus.modules.paymentlifecycle.repository.PaymentRepository;
import com.sepanexus.modules.paymentlifecycle.service.PaymentHistoryRecorder;
import com.sepanexus.modules.paymentlifecycle.service.TenantGucConfigurer;
import com.sepanexus.shared.ClockPort;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
public class InboxConsumer {

    private static final Logger log = LoggerFactory.getLogger(InboxConsumer.class);

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PaymentRepository paymentRepository;
    private final TenantGucConfigurer tenantGucConfigurer;
    private final PaymentHistoryRecorder paymentHistoryRecorder;
    private final ClockPort clockPort;

    public InboxConsumer(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate, PaymentRepository paymentRepository,
            TenantGucConfigurer tenantGucConfigurer, PaymentHistoryRecorder paymentHistoryRecorder,
            ClockPort clockPort) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.paymentRepository = paymentRepository;
        this.tenantGucConfigurer = tenantGucConfigurer;
        this.paymentHistoryRecorder = paymentHistoryRecorder;
        this.clockPort = clockPort;
    }

    @KafkaListener(id = "payment-lifecycle-inbox", topics = PaymentLifecycleTopicConfig.TOPIC,
            groupId = "payment-lifecycle-inbox")
    @Transactional
    public void consume(String payload) {
        PaymentLifecycleEvent event = parse(payload);
        int inserted = jdbcTemplate.update("""
                INSERT INTO payment.inbox_events (source_event_id)
                VALUES (?)
                ON CONFLICT (source_event_id) DO NOTHING
                """, event.eventId());
        if (inserted == 0) {
            log.info("Ignoring duplicate payment lifecycle event {}", event.eventId());
            return;
        }

        tenantGucConfigurer.apply(event.tenantId());
        paymentRepository.findById(event.aggregateId()).ifPresent(payment -> {
            PaymentStatus fromStatus = payment.getStatus();
            payment.markValidated();
            paymentHistoryRecorder.recordTransition(payment.getId(), fromStatus, payment.getStatus(), "INTERNAL",
                    event.eventId(), event.eventType(), clockPort.now());
        });
    }

    private PaymentLifecycleEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, PaymentLifecycleEvent.class);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid payment lifecycle event payload", exception);
        }
    }
}

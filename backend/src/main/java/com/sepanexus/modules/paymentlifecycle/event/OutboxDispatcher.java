package com.sepanexus.modules.paymentlifecycle.event;

import com.sepanexus.modules.paymentlifecycle.domain.OutboxEvent;
import com.sepanexus.modules.paymentlifecycle.repository.OutboxEventRepository;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxEventRepository outboxEvents;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxDispatcher(OutboxEventRepository outboxEvents, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEvents = outboxEvents;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void dispatch() {
        List<OutboxEvent> unpublished = outboxEvents.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent event : unpublished) {
            try {
                kafkaTemplate.send(PaymentLifecycleTopicConfig.TOPIC, event.getAggregateId().toString(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);
                event.markPublished();
            } catch (Exception exception) {
                log.warn("Outbox event {} remains unpublished after Kafka publication failure", event.getId(), exception);
            }
        }
    }
}

package com.sepanexus.modules.paymentlifecycle.isoadapter;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * EPIC-27 Story 27.2C/27.4: registers the two {@code iso-adapter}-owned topics per §3.7 v2 Kafka
 * Topic Catalog (ADR-N8) — {@code iso.message.correlated} (key {@code payment_id}) and {@code
 * iso.message.orphaned} (key {@code iso_message_id}, terminal operator queue). Topic names are
 * sourced from {@link IsoOutboxEventType}, never repeated as separate literals here.
 */
@Configuration
public class IsoCorrelationTopicConfig {

    public static final String TOPIC = IsoOutboxEventType.MESSAGE_CORRELATED.topic();

    @Bean
    NewTopic isoMessageCorrelatedTopic() {
        return TopicBuilder.name(IsoOutboxEventType.MESSAGE_CORRELATED.topic()).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic isoMessageOrphanedTopic() {
        return TopicBuilder.name(IsoOutboxEventType.MESSAGE_ORPHANED.topic()).partitions(1).replicas(1).build();
    }
}

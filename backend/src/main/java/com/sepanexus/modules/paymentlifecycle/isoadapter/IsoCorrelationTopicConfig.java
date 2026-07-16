package com.sepanexus.modules.paymentlifecycle.isoadapter;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * EPIC-27 Story 27.2C: {@code iso.message.correlated} — canonical name per §3.7 v2 Kafka Topic
 * Catalog (ADR-N8), producer-owner {@code iso-adapter}, key {@code payment_id}, {@code [MVP]}.
 */
@Configuration
public class IsoCorrelationTopicConfig {

    public static final String TOPIC = "iso.message.correlated";

    @Bean
    NewTopic isoMessageCorrelatedTopic() {
        return TopicBuilder.name(TOPIC).partitions(1).replicas(1).build();
    }
}

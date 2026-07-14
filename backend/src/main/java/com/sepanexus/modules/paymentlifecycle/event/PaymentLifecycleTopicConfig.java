package com.sepanexus.modules.paymentlifecycle.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class PaymentLifecycleTopicConfig {

    /** Canonical name per §3.7 v2 Kafka Topic Catalog (ADR-N8) — the sole source of truth for topic names. */
    public static final String TOPIC = "payment.validated";

    @Bean
    NewTopic paymentLifecycleEventsTopic() {
        return TopicBuilder.name(TOPIC).partitions(1).replicas(1).build();
    }
}

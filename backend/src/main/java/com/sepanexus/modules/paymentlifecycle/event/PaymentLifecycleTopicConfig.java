package com.sepanexus.modules.paymentlifecycle.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class PaymentLifecycleTopicConfig {

    /** Canonical name per §3.7 v2 Kafka Topic Catalog (ADR-N8) — the sole source of truth for topic names. */
    public static final String RECEIVED_TOPIC = "payment.received";
    public static final String VALIDATED_TOPIC = "payment.validated";
    public static final String STATUS_REPORTED_TOPIC = "payment.status.reported";

    @Bean
    NewTopic paymentReceivedTopic() {
        return TopicBuilder.name(RECEIVED_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic paymentValidatedTopic() {
        return TopicBuilder.name(VALIDATED_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic paymentStatusReportedTopic() {
        return TopicBuilder.name(STATUS_REPORTED_TOPIC).partitions(1).replicas(1).build();
    }
}

package com.sepanexus.modules.paymentlifecycle.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class PaymentLifecycleTopicConfig {

    public static final String TOPIC = "payment.lifecycle.events.v1";

    @Bean
    NewTopic paymentLifecycleEventsTopic() {
        return TopicBuilder.name(TOPIC).partitions(1).replicas(1).build();
    }
}

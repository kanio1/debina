package com.sepanexus.modules.paymentlifecycle.event;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * Proves the gauge is a real, non-vacuous measurement: pausing the one real consumer
 * (`payment-lifecycle-inbox`), producing a message, and observing lag > 0, then resuming and
 * observing lag drop back to 0 once the message is actually consumed.
 */
class KafkaConsumerGroupLagGaugeTest extends KafkaIntegrationSupport {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Test
    void reportsRealLagThatDropsAfterConsumption() throws Exception {
        MessageListenerContainer container = listenerRegistry.getListenerContainer("payment-lifecycle-inbox");
        assertThat(container).isNotNull();

        container.stop();
        eventually(() -> assertThat(container.isRunning()).isFalse());

        String payload = """
                {"eventId":"%s","aggregateId":"%s","tenantId":"%s","eventType":"PAYMENT_SUBMITTED"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()).strip();

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(PaymentLifecycleTopicConfig.RECEIVED_TOPIC, payload)).get();
        }

        double lagWhilePaused = meterRegistry.get("kafka.consumer.lag")
                .tag("group", KafkaConsumerGroupLagGauge.GROUP_ID).gauge().value();
        assertThat(lagWhilePaused).isGreaterThan(0);

        container.start();
        eventually(() -> {
            double lagAfterResume = meterRegistry.get("kafka.consumer.lag")
                    .tag("group", KafkaConsumerGroupLagGauge.GROUP_ID).gauge().value();
            assertThat(lagAfterResume).isEqualTo(0.0);
        });
    }
}

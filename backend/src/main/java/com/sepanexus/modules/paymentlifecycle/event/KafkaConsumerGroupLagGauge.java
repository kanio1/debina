package com.sepanexus.modules.paymentlifecycle.event;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/**
 * EPIC-25 Story 25.3 (narrowed scope): lag-per-consumer-group as a first-class metric for the
 * one real consumer group in this codebase, {@code payment-lifecycle-inbox}. Deliberately does
 * NOT attempt retry-count/DLQ-depth or an alert rule — no DLQ mechanism and no
 * {@code csm.response}/reconciliation topics exist yet to alert on (see the epic file's
 * `[PLANNING-DEFECT]` note); building those now would be inventing an alerting target that
 * doesn't exist.
 */
@Component
public class KafkaConsumerGroupLagGauge {

    static final String GROUP_ID = "payment-lifecycle-inbox";

    private final KafkaAdmin kafkaAdmin;

    public KafkaConsumerGroupLagGauge(KafkaAdmin kafkaAdmin, MeterRegistry meterRegistry) {
        this.kafkaAdmin = kafkaAdmin;
        Gauge.builder("kafka.consumer.lag", this, KafkaConsumerGroupLagGauge::currentLag)
                .tag("group", GROUP_ID)
                .tag("topic", PaymentLifecycleTopicConfig.TOPIC)
                .description("Sum of (end offset - committed offset) across all partitions of "
                        + PaymentLifecycleTopicConfig.TOPIC + " for consumer group " + GROUP_ID)
                .register(meterRegistry);
    }

    long currentLag() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            TopicDescription description = adminClient.describeTopics(List.of(PaymentLifecycleTopicConfig.TOPIC))
                    .topicNameValues().get(PaymentLifecycleTopicConfig.TOPIC).get();
            List<TopicPartition> partitions = description.partitions().stream()
                    .map(partitionInfo -> new TopicPartition(PaymentLifecycleTopicConfig.TOPIC, partitionInfo.partition()))
                    .toList();

            Map<TopicPartition, OffsetAndMetadata> committed = adminClient
                    .listConsumerGroupOffsets(GROUP_ID).partitionsToOffsetAndMetadata().get();

            Map<TopicPartition, OffsetSpec> latestRequest = partitions.stream()
                    .collect(java.util.stream.Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));
            Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                    adminClient.listOffsets(latestRequest).all().get();

            long totalLag = 0;
            for (TopicPartition partition : partitions) {
                long endOffset = endOffsets.get(partition).offset();
                long committedOffset = committed.containsKey(partition) ? committed.get(partition).offset() : 0L;
                totalLag += Math.max(0, endOffset - committedOffset);
            }
            return totalLag;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while computing consumer lag", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Could not compute consumer lag for group " + GROUP_ID, exception.getCause());
        }
    }
}

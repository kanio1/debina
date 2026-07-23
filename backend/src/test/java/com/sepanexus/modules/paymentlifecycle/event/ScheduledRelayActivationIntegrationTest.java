package com.sepanexus.modules.paymentlifecycle.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/** A dedicated Testcontainers context is the only test context which enables relay scheduling. */
@SpringBootTest(properties = "sepa.scheduling.enabled=true")
@org.junit.jupiter.api.Tag("testcontainers")
class ScheduledRelayActivationIntegrationTest extends KafkaIntegrationSupport {

    @Autowired
    private OutboxRelayScheduler scheduler;

    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduledAnnotationBeanPostProcessor;

    @Test
    void enabledSchedulingRegistersOnlyTheTwoRelayTasks() {
        assertThat(scheduler).isNotNull();
        assertThat(scheduledAnnotationBeanPostProcessor.getScheduledTasks())
                .extracting(Object::toString)
                .contains("com.sepanexus.modules.paymentlifecycle.event.OutboxRelayScheduler.relayPaymentOutbox",
                        "com.sepanexus.modules.paymentlifecycle.event.OutboxRelayScheduler.relayIsoOutbox");
    }
}

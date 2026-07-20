package com.sepanexus.modules.paymentlifecycle.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** The shared test property disables relay scheduling unless a dedicated test opts in. */
class SchedulingDisabledIntegrationTest extends KafkaIntegrationSupport {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void ordinaryIntegrationContextsDoNotCreateTheRelayScheduler() {
        assertThat(applicationContext.getBeansOfType(OutboxRelayScheduler.class)).isEmpty();
    }
}

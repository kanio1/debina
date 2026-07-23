package com.sepanexus.modules.paymentlifecycle.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("fast")
class PaymentOutboxEventTypeTest {

    @Test
    void mapsOnlyTheTwoSourceBackedPaymentFactsToTheirOwnedTopics() {
        assertThat(PaymentOutboxEventType.fromEventType("payment.received.v1"))
                .contains(PaymentOutboxEventType.PAYMENT_RECEIVED);
        assertThat(PaymentOutboxEventType.fromEventType("payment.validated.v1"))
                .contains(PaymentOutboxEventType.PAYMENT_VALIDATED);
        assertThat(PaymentOutboxEventType.fromEventType("payment.submitted.v1")).isEmpty();
        assertThat(PaymentOutboxEventType.fromEventType("payment.unknown.v1")).isEmpty();
    }

    @Test
    void retainsTheCatalogTopicForEachKnownFact() {
        assertThat(PaymentOutboxEventType.PAYMENT_RECEIVED.topic()).isEqualTo("payment.received");
        assertThat(PaymentOutboxEventType.PAYMENT_VALIDATED.topic()).isEqualTo("payment.validated");
    }
}

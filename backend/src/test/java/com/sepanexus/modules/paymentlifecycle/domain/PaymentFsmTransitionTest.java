package com.sepanexus.modules.paymentlifecycle.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentFsmTransitionTest {

    @Test
    void receivedToValidatedIsLegal() {
        PaymentEntity payment = newReceivedPayment();

        assertThatCode(payment::markValidated).doesNotThrowAnyException();
        assertThatCode(() -> assertStatus(payment, PaymentStatus.VALIDATED)).doesNotThrowAnyException();
    }

    @Test
    void validatedToValidatedSelfLoopIsIllegal() {
        PaymentEntity payment = newReceivedPayment();
        payment.markValidated();

        assertThatThrownBy(payment::markValidated)
                .isInstanceOf(IllegalPaymentTransitionException.class)
                .hasMessageContaining("VALIDATED -> VALIDATED");
    }

    @Test
    void terminalRejectedCannotTransitionBackToValidated() {
        PaymentTransitionTable.requireLegal(PaymentStatus.RECEIVED, PaymentStatus.REJECTED);

        assertThatThrownBy(() -> PaymentTransitionTable.requireLegal(PaymentStatus.REJECTED, PaymentStatus.VALIDATED))
                .isInstanceOf(IllegalPaymentTransitionException.class)
                .hasMessageContaining("REJECTED -> VALIDATED");
    }

    @Test
    void terminalDispatchedHasNoOutgoingTransitions() {
        assertThatThrownBy(() -> PaymentTransitionTable.requireLegal(PaymentStatus.DISPATCHED, PaymentStatus.RECEIVED))
                .isInstanceOf(IllegalPaymentTransitionException.class);
    }

    private static void assertStatus(PaymentEntity payment, PaymentStatus expected) {
        if (payment.getStatus() != expected) {
            throw new AssertionError("Expected " + expected + " but was " + payment.getStatus());
        }
    }

    private static PaymentEntity newReceivedPayment() {
        return PaymentEntity.received(UUID.randomUUID(), null, "e2e-fsm-1", new BigDecimal("10.00"), "EUR",
                "DE89370400440532013000", "FR7630006000011234567890189", java.time.Instant.now());
    }
}

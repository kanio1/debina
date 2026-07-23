package com.sepanexus.modules.paymentlifecycle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sepanexus.modules.paymentlifecycle.domain.PaymentStatus;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentTransitionTable;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The payment FSM describes legal business transitions only. Settlement finality is settlement-owned
 * and profile-configured; until that authority exists, no payment-lifecycle history transition may
 * claim it. See the frozen five-axis rule in the main blueprint §3.11/§4.11.
 */
@ExtendWith(MockitoExtension.class)
@org.junit.jupiter.api.Tag("fast")
class TerminalBusinessStatusDoesNotImplyFinalityTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void receivedDoesNotImplyFinality() {
        assertTransitionIsNotFinal(PaymentStatus.RECEIVED);
    }

    @Test
    void validatedDoesNotImplyFinality() {
        assertTransitionIsNotFinal(PaymentStatus.VALIDATED);
    }

    @Test
    void rejectedDoesNotImplySettlementFinalityEvenThoughItsFsmHasNoOutgoingTransition() {
        assertThat(PaymentTransitionTable.hasNoLegalOutgoingTransitions(PaymentStatus.REJECTED)).isTrue();
        assertTransitionIsNotFinal(PaymentStatus.REJECTED);
    }

    @Test
    void dispatchedDoesNotImplyFinalityEvenThoughItsFsmHasNoOutgoingTransition() {
        assertThat(PaymentTransitionTable.hasNoLegalOutgoingTransitions(PaymentStatus.DISPATCHED)).isTrue();
        assertTransitionIsNotFinal(PaymentStatus.DISPATCHED);
    }

    private void assertTransitionIsNotFinal(PaymentStatus status) {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), any(UUID.class))).thenReturn(0);
        PaymentHistoryRecorder recorder = new PaymentHistoryRecorder(jdbcTemplate);

        recorder.recordTransition(UUID.randomUUID(), null, status, "INTERNAL", UUID.randomUUID(),
                "payment.test.transition", Instant.parse("2026-07-20T12:00:00Z"));

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(any(String.class), arguments.capture());
        assertThat(arguments.getValue()[6]).as("is_final written for %s", status).isEqualTo(false);
    }
}

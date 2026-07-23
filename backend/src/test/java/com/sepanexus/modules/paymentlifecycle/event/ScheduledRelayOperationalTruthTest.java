package com.sepanexus.modules.paymentlifecycle.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.sepanexus.modules.paymentlifecycle.isoadapter.IsoOutboxDispatcher;
import com.sepanexus.shared.ClockPort;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

@org.junit.jupiter.api.Tag("fast")
class ScheduledRelayOperationalTruthTest {

    private final OutboxDispatcher paymentRelay = mock(OutboxDispatcher.class);
    private final IsoOutboxDispatcher isoRelay = mock(IsoOutboxDispatcher.class);
    private final OutboxRelayOperationalState state = new OutboxRelayOperationalState();
    private final OutboxRelayHealthIndicator health = new OutboxRelayHealthIndicator(state);
    private Instant now = Instant.parse("2026-07-20T10:00:00Z");
    private final ClockPort clock = () -> now;
    private final OutboxRelayScheduler scheduler = new OutboxRelayScheduler(paymentRelay, isoRelay, state, clock);

    @Test
    void successIsUpDatabasePermissionFailureIsDownAndLaterSuccessRecovers() {
        doNothing().when(paymentRelay).dispatch();
        scheduler.relayPaymentOutbox();
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);

        now = now.plusSeconds(1);
        doThrow(new OutboxRelayPublicationException("payment", UUID.randomUUID(),
                new SQLException("permission denied", "42501"))).when(paymentRelay).dispatch();
        scheduler.relayPaymentOutbox();
        assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.health().getDetails()).containsEntry("lastFailureCategory", "DATABASE_PERMISSION");

        now = now.plusSeconds(1);
        doNothing().when(paymentRelay).dispatch();
        scheduler.relayPaymentOutbox();
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void brokerFailureIsVisibleAsDownWithoutEscapingTheScheduledBoundary() {
        doThrow(new OutboxRelayPublicationException("payment", UUID.randomUUID(),
                new KafkaException("controlled broker failure"))).when(paymentRelay).dispatch();

        scheduler.relayPaymentOutbox();

        assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.health().getDetails()).containsEntry("lastFailureCategory", "BROKER_UNAVAILABLE");
    }
}

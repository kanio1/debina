package com.sepanexus.modules.paymentlifecycle.event;

import com.sepanexus.modules.paymentlifecycle.isoadapter.IsoOutboxDispatcher;
import com.sepanexus.shared.ClockPort;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The only scheduled relay entrypoint; individual dispatchers remain explicitly callable. */
@Component
@ConditionalOnProperty(prefix = "sepa.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxDispatcher paymentRelay;
    private final IsoOutboxDispatcher isoRelay;
    private final OutboxRelayOperationalState state;
    private final ClockPort clockPort;

    public OutboxRelayScheduler(OutboxDispatcher paymentRelay, IsoOutboxDispatcher isoRelay,
            OutboxRelayOperationalState state, ClockPort clockPort) {
        this.paymentRelay = paymentRelay;
        this.isoRelay = isoRelay;
        this.state = state;
        this.clockPort = clockPort;
    }

    @Scheduled(fixedDelayString = "${sepa.scheduling.relay-fixed-delay-ms:2000}")
    public void relayPaymentOutbox() {
        run("payment", paymentRelay::dispatch);
    }

    @Scheduled(fixedDelayString = "${sepa.scheduling.relay-fixed-delay-ms:2000}")
    public void relayIsoOutbox() {
        run("iso", isoRelay::dispatch);
    }

    private void run(String relayName, Runnable relay) {
        try {
            relay.run();
            state.recordSuccess(relayName, clockPort.now());
        } catch (RuntimeException exception) {
            OutboxRelayFailureCategory category = categoryOf(exception);
            state.recordFailure(relayName, clockPort.now(), category);
            log.warn("Outbox relay {} run failed with category {}", relayName, category);
        }
    }

    private static OutboxRelayFailureCategory categoryOf(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException) {
                if ("42501".equals(sqlException.getSQLState())) {
                    return OutboxRelayFailureCategory.DATABASE_PERMISSION;
                }
                if (sqlException.getSQLState() != null && sqlException.getSQLState().startsWith("08")) {
                    return OutboxRelayFailureCategory.DATABASE_CONNECTIVITY;
                }
            }
            if (current instanceof java.util.concurrent.TimeoutException
                    || current instanceof org.apache.kafka.common.KafkaException) {
                return OutboxRelayFailureCategory.BROKER_UNAVAILABLE;
            }
            if (current instanceof IllegalArgumentException
                    && current.getMessage() != null && current.getMessage().contains("Unrecognized iso outbox event type")) {
                return OutboxRelayFailureCategory.UNKNOWN_EVENT_TYPE;
            }
        }
        return OutboxRelayFailureCategory.UNEXPECTED;
    }
}

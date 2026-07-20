package com.sepanexus.modules.paymentlifecycle.event;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** Thread-safe, payload-free operational truth for scheduled outbox relay runs. */
@Component
public class OutboxRelayOperationalState {

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.initial());

    public void recordSuccess(Instant completedAt) {
        snapshot.updateAndGet(current -> new Snapshot(completedAt, current.lastFailureAt(),
                current.lastFailureCategory(), 0));
    }

    public void recordFailure(Instant failedAt, OutboxRelayFailureCategory category) {
        snapshot.updateAndGet(current -> new Snapshot(current.lastSuccessfulRun(), failedAt, category,
                current.consecutiveFailures() + 1));
    }

    public Snapshot snapshot() {
        return snapshot.get();
    }

    public record Snapshot(Instant lastSuccessfulRun, Instant lastFailureAt,
            OutboxRelayFailureCategory lastFailureCategory, int consecutiveFailures) {
        private static Snapshot initial() {
            return new Snapshot(null, null, null, 0);
        }

        boolean hasCurrentFailure() {
            return lastFailureAt != null && (lastSuccessfulRun == null || lastFailureAt.isAfter(lastSuccessfulRun));
        }
    }
}

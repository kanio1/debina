package com.sepanexus.modules.paymentlifecycle.event;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Thread-safe, payload-free operational truth for scheduled outbox relay runs. */
@Component
public class OutboxRelayOperationalState {

    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();

    public void recordSuccess(String relayName, Instant completedAt) {
        snapshots.compute(relayName, (ignored, current) -> {
            Snapshot previous = current == null ? Snapshot.initial() : current;
            return new Snapshot(completedAt, previous.lastFailureAt(), previous.lastFailureCategory(), 0);
        });
    }

    public void recordFailure(String relayName, Instant failedAt, OutboxRelayFailureCategory category) {
        snapshots.compute(relayName, (ignored, current) -> {
            Snapshot previous = current == null ? Snapshot.initial() : current;
            return new Snapshot(previous.lastSuccessfulRun(), failedAt, category, previous.consecutiveFailures() + 1);
        });
    }

    public Map<String, Snapshot> snapshots() {
        return Map.copyOf(snapshots);
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

package com.sepanexus.modules.paymentlifecycle.event;

import java.util.Map;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("outboxRelay")
public class OutboxRelayHealthIndicator implements HealthIndicator {

    private final OutboxRelayOperationalState state;

    public OutboxRelayHealthIndicator(OutboxRelayOperationalState state) {
        this.state = state;
    }

    @Override
    public Health health() {
        Map<String, OutboxRelayOperationalState.Snapshot> snapshots = state.snapshots();
        Map<String, OutboxRelayOperationalState.Snapshot> failures = snapshots.entrySet().stream()
                .filter(entry -> entry.getValue().hasCurrentFailure())
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!failures.isEmpty()) {
            Health.Builder health = Health.down()
                    .withDetail("failedRelays", failures.keySet())
                    .withDetail("consecutiveFailures", failures.values().stream()
                            .mapToInt(OutboxRelayOperationalState.Snapshot::consecutiveFailures).sum());
            if (failures.size() == 1) {
                health.withDetail("lastFailureCategory", failures.values().iterator().next().lastFailureCategory().name());
            }
            return health
                    .build();
        }
        if (snapshots.isEmpty() || snapshots.values().stream()
                .allMatch(snapshot -> snapshot.lastSuccessfulRun() == null)) {
            return Health.unknown().withDetail("state", "NO_RUN").build();
        }
        return Health.up().withDetail("consecutiveFailures", 0).build();
    }
}

package com.sepanexus.modules.paymentlifecycle.event;

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
        OutboxRelayOperationalState.Snapshot snapshot = state.snapshot();
        if (snapshot.hasCurrentFailure()) {
            return Health.down()
                    .withDetail("lastFailureCategory", snapshot.lastFailureCategory().name())
                    .withDetail("consecutiveFailures", snapshot.consecutiveFailures())
                    .build();
        }
        if (snapshot.lastSuccessfulRun() == null) {
            return Health.unknown().withDetail("state", "NO_RUN").build();
        }
        return Health.up().withDetail("consecutiveFailures", snapshot.consecutiveFailures()).build();
    }
}

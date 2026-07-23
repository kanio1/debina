package com.sepanexus;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("fast")
class SchedulerActivationGuardTest {

    @Test
    void relaySchedulingIsConditionalAndNeitherDispatcherNorEgressClaimIsScheduled() throws Exception {
        String scheduler = Files.readString(Path.of("src/main/java/com/sepanexus/modules/paymentlifecycle/event/OutboxRelayScheduler.java"));
        String payment = Files.readString(Path.of("src/main/java/com/sepanexus/modules/paymentlifecycle/event/OutboxDispatcher.java"));
        String iso = Files.readString(Path.of("src/main/java/com/sepanexus/modules/paymentlifecycle/isoadapter/IsoOutboxDispatcher.java"));
        String egress = Files.readString(Path.of("src/main/java/com/sepanexus/egress/internal/OutboundMessageDispatcher.java"));

        assertThat(scheduler).contains("@ConditionalOnProperty(prefix = \"sepa.scheduling\", name = \"enabled\", havingValue = \"true\", matchIfMissing = true)");
        assertThat(scheduler).contains("@Scheduled");
        assertThat(payment).doesNotContain("\n    @Scheduled");
        assertThat(iso).doesNotContain("\n    @Scheduled");
        assertThat(egress).doesNotContain("\n    @Scheduled");
    }
}

package com.sepanexus;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RuntimeDatasourceOwnershipTest {

    private static final Path PAYMENT_RELAY = Path.of(
            "src/main/java/com/sepanexus/modules/paymentlifecycle/event/OutboxDispatcher.java");
    private static final Path ISO_RELAY = Path.of(
            "src/main/java/com/sepanexus/modules/paymentlifecycle/isoadapter/IsoOutboxDispatcher.java");
    private static final Path SCHEDULER = Path.of(
            "src/main/java/com/sepanexus/modules/paymentlifecycle/event/OutboxRelayScheduler.java");

    @Test
    void relayDispatchersUseOnlyTheDedicatedJdbcTemplateAndTransactionManager() throws IOException {
        assertRelaySource(PAYMENT_RELAY);
        assertRelaySource(ISO_RELAY);
    }

    @Test
    void fixturesProveTheQualifierRuleIsNotVacuous() throws IOException {
        assertThat(violatesDomainRelayBoundary(Path.of(
                "src/test/java/com/sepanexus/runtimefixture/AllowedRelayFixture.java"))).isFalse();
        assertThat(violatesDomainRelayBoundary(Path.of(
                "src/test/java/com/sepanexus/runtimefixture/ForbiddenDomainUsesRelayFixture.java"))).isTrue();
    }

    @Test
    void schedulerDelegatesOnlyToTheTransactionQualifiedRelayDispatchers() throws IOException {
        String scheduler = Files.readString(SCHEDULER);
        assertThat(scheduler).contains("run(\"payment\", paymentRelay::dispatch)");
        assertThat(scheduler).contains("run(\"iso\", isoRelay::dispatch)");
        assertThat(scheduler).doesNotContain("JdbcTemplate");
        assertThat(scheduler).doesNotContain("TransactionManager");
    }

    private static void assertRelaySource(Path sourcePath) throws IOException {
        String source = Files.readString(sourcePath);
        assertThat(source).contains("@Qualifier(\"outboxRelayJdbcTemplate\")");
        assertThat(source).contains("@Transactional(transactionManager = \"outboxRelayTransactionManager\")");
        assertThat(source).contains("FOR UPDATE SKIP LOCKED");
        assertThat(source).doesNotContain("Repository");
        assertThat(source).doesNotContain("EntityManager");
    }

    private static boolean violatesDomainRelayBoundary(Path sourcePath) throws IOException {
        String source = Files.readString(sourcePath);
        return source.contains("package com.sepanexus.modules.paymentlifecycle.service")
                && source.contains("outboxRelayJdbcTemplate");
    }
}

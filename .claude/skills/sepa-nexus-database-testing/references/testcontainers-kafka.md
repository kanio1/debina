# Kafka Testcontainers — standard shape in this repo

Real example: `backend/src/test/java/com/sepanexus/modules/paymentlifecycle/event/KafkaIntegrationSupport.java`.

```java
static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:latest");

@DynamicPropertySource
static synchronized void properties(DynamicPropertyRegistry registry) {
    if (!initialized) {
        POSTGRES.start();
        KAFKA.start();
        // ... Flyway migrate ...
        initialized = true;
    }
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
}
```

## When a real Kafka container is actually needed

Reserve it for tests where broker semantics genuinely matter to the behavior under test: message ordering within a partition, at-least-once redelivery, consumer group rebalancing, outbox-relay-to-Kafka round trips (see `sepa-nexus-payments-data-integrity` skill's `outbox-inbox.md`). A test that only needs to verify "does my service method call `KafkaTemplate.send()` with the right payload" doesn't need a real broker — a narrower unit test with a mock/spy of the Spring `KafkaTemplate` is more appropriate and faster; don't reach for Testcontainers-Kafka by default for every Kafka-adjacent test.

## Polling for asynchronous delivery

Kafka consumption is asynchronous relative to the producing test — never assert immediately after producing. `KafkaIntegrationSupport.eventually(...)` is this repo's existing retry-with-timeout helper (polls every 100ms up to a 5s deadline) — use it (or the same pattern) rather than a fixed `Thread.sleep()`, which is both slower than necessary on the happy path and flaky under load.

## Consumer group isolation between tests

If multiple tests share a Kafka container (see the `initialized`-guard sharing pattern in `testcontainers-postgres.md`), make sure each test uses a distinct topic and/or consumer group so message consumption from one test can't be observed by (or interfere with) another running concurrently — do not assume test execution order or exclusivity.

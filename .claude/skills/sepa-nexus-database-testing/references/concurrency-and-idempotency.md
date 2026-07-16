# Concurrency and idempotency tests

See `sepa-nexus-payments-data-integrity` skill's `idempotency.md` for the required behavior matrix — this file is the test-writing pattern specifically.

## Sequential duplicate (necessary but not sufficient)

```java
@Test
void sameKeySamePayloadReplaysWithoutReprocessing() {
    var first = submit(idempotencyKey, payload);
    var second = submit(idempotencyKey, payload);
    assertEquals(first.paymentId(), second.paymentId());
    assertEquals(1, countPaymentsWithKey(idempotencyKey)); // not 2
}
```

This proves the lookup-before-insert path works when there's no race — necessary, but does not prove safety under real concurrency.

## Concurrent duplicate (the test that actually matters)

```java
@Test
void concurrentSameKeyRequestsProduceExactlyOneSideEffect() throws Exception {
    var executor = Executors.newFixedThreadPool(2);
    var barrier = new CyclicBarrier(2); // force both to submit near-simultaneously
    Callable<Result> task = () -> {
        barrier.await();
        return submit(idempotencyKey, payload);
    };
    var futures = executor.invokeAll(List.of(task, task));
    // both calls return the SAME payment ID
    // exactly ONE row exists in the database, not two
    assertEquals(1, countPaymentsWithKey(idempotencyKey));
}
```

Run this against a real PostgreSQL Testcontainers instance, relying on a database-level unique constraint on the idempotency scope tuple (`tenant_id, operation, idempotency_key`) with the insert-conflict path routed to "look up and return the existing result" — application-level locking alone (e.g. a Java `synchronized` block) does not protect against two separate application instances/pods in a real deployment, and a test that only proves application-level locking works is proving the wrong thing.

## What a passing concurrent test must show, precisely

- Exactly one row was created (not "one succeeded, one failed" as a raw error — the second request should gracefully replay, not blow up).
- Both callers received a result referring to the *same* underlying entity/ID.
- No partial/inconsistent state (e.g. one side effect applied twice because it happened before the uniqueness check could kick in — check any downstream side effect, like an outbox event, was also emitted exactly once).

## Different payload, same key

```java
@Test
void sameKeyDifferentPayloadConflicts() {
    submit(idempotencyKey, payloadA);
    assertThrows(IdempotencyConflictException.class, () -> submit(idempotencyKey, payloadB));
}
```

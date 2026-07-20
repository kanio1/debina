# Runtime proof matrix

Use PostgreSQL 18 and real Kafka/Testcontainers evidence where the change affects delivery or business state. Cover applicable producer success and broker acknowledgement; permission failure; retry/recovery; duplicate publication and consumption; crashes before/after database commit and offset commit; partition ordering; multi-tenant isolation; schema-version compatibility; consumer-group behavior; relay/scheduler health; no published marker on failure; mutation proof; and log/metric inspection.

For each proof, assert the durable database state, broker/offset state, inbox/idempotency result, absence of duplicate business effect, owner role/datasource, and observable failure/recovery. Do not call fixture files, mocked Kafka, or a green unit suite runtime delivery evidence.

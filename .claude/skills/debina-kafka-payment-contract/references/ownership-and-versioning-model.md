# Ownership and versioning model

ADR-N8 makes §3.7 v2 the sole topic/AsyncAPI source: one producer owner, explicit key and ordering, inbox-gated consumer groups, and per-consumer-group DLQ behavior. ADR-N5 requires module-owned `<schema>.outbox_events` and `<schema>.inbox_events`; a narrow dispatcher may claim/mark outbox records but has no domain-table ownership.

Choose a partition key only from a documented ordering invariant; do not infer it from a class name. Name producer and consumer ownership, real runtime role/datasource identity, schema version and compatibility policy, and explicit database/offset transaction boundary. Consumer processing cannot use direct cross-schema writes. Version changes must be contract-reviewed against known producers and consumers; absent compatibility policy is `SOURCE-BLOCKED`.

`payment.sla.breached` remains unavailable unless an accepted catalog row and business timing policy define its owner, schema, key, consumers, threshold and semantics. The current planning blocker is not permission to create a telemetry or business event.

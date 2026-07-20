# Delivery and offset failure matrix

Document the durable state before and after each boundary, recovery action, duplicate behavior, and visible operator signal.

| Window | Required review question |
| --- | --- |
| DB commit before producer send | Is eligible outbox state recoverable by the relay? |
| Producer send before broker acknowledgement | Can retry occur without marking published? |
| Broker acknowledgement before local published marker | Can re-dispatch safely after crash? |
| Consumer receive before DB commit | Is no business effect durable before processing commits? |
| Consumer DB commit before offset commit | Is redelivery deduplicated/replay-safe? |
| Offset commit before durable processing | Why is this forbidden or prevented? |
| Redelivery after crash | Does inbox/idempotency prevent a second business effect? |
| Poison message/schema incompatibility | What approved handling, visibility, and operator contract applies? |
| Permission loss/broker unavailable/scheduler failure | Is state retained, health observable, and recovery proven? |

Never present an untested window as exactly-once business semantics.

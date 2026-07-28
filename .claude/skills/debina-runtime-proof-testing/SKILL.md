---
name: debina-runtime-proof-testing
description: Use when claiming a flow done, verifying payment features, migrations, transaction boundaries, Kafka/outbox/inbox, RLS/grants, finality, concurrency, commits, or handoffs; do not replace focused unit-test guidance for trivial pure functions.
---

# Runtime proof testing

No completion claim without fresh evidence covering the real runtime boundary. A green `mvn test` alone is insufficient for STANDARD/DECISION money, finality, broker, or schema work. Map applicable proof links: trigger → validation → authentication → authorization → decision → state → database → money → outbox/event → broker acknowledgement → consumer/retry/duplicate → failure window → finality → reconciliation → audit.

## Lane-aware proof selection

Read `work/ACTIVE.json` lane when present.

### FAST

- targeted unit/component test for the changed risk;
- `./tools/agent/verify-fast`;
- final diff check (`git diff --check` / `./tools/agent/final-check`).

### STANDARD

- targeted test for the changed behaviour;
- integration or contract test when the boundary is PostgreSQL, Kafka, HTTP, or OpenAPI/AsyncAPI;
- appropriate runtime/smoke for the touched path;
- independent review before DONE.

### DECISION or CRITICAL

- full runtime proof chain for the decided boundary;
- failure windows and crash window where process death can leave partial state;
- concurrency/replay where relevant;
- tenant/GUC/runtime role/grants proof when security context matters;
- fresh/upgrade migration when schema changes;
- mutation proof for critical invariants;
- two sequential full backend regressions only for completed critical slices when the risk justifies them — never require two sequential full regressions for every small FAST task.

## Status vocabulary

Use only `VERIFIED`, `IMPLEMENTED-BUT-UNVERIFIED`, `INFRASTRUCTURE-BLOCKED`, `DECISION-BLOCKED`, `SOURCE-BLOCKED`, `BUDGET-LIMITED-CHECKPOINT`, or `FAILED`. Never translate a blocker to success or claim unrun tests/evals.

For each mutation state the invariant, make one reversible change, run its named test, confirm the intended failure, revert immediately, rerun, and scan residue. Before completion of STANDARD/DECISION work run fresh targeted evidence, inspect output/counts/logs, restore generated files if touched, validate planning/skills when those paths changed, inspect diff/status, and update handoff when the session was material. Read [proof chain](references/runtime-proof-chain.md), [evidence matrix](references/test-evidence-matrix.md), [mutation protocol](references/mutation-proof-protocol.md), and [completion gate](references/completion-claim-gate.md).

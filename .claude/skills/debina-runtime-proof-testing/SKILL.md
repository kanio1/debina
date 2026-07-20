---
name: debina-runtime-proof-testing
description: Use when claiming a flow done, verifying payment features, migrations, transaction boundaries, Kafka/outbox/inbox, RLS/grants, finality, concurrency, commits, or handoffs; do not replace focused unit-test guidance for trivial pure functions.
---
# Runtime proof testing

No completion claim without fresh evidence covering the real runtime boundary. A green `mvn test` alone is insufficient. Map applicable proof links: trigger → validation → authentication → authorization → decision → state → database → money → outbox/event → broker acknowledgement → consumer/retry/duplicate → failure window → finality → reconciliation → audit.

Select evidence from structural/semantic RED, targeted GREEN, PostgreSQL 18 Testcontainers, fresh/upgrade migration with data, tenant/GUC/runtime role/grants, success/business/technical failure, replay/conflict/concurrency/crash window/rollback/partial state, architecture, broker/scheduler, logs/metrics, mutations, cleanup, `git diff --check`, generated restoration, planning validation and two sequential full backend regressions for completed critical slices.

Use only `VERIFIED`, `IMPLEMENTED-BUT-UNVERIFIED`, `INFRASTRUCTURE-BLOCKED`, `DECISION-BLOCKED`, `SOURCE-BLOCKED`, `BUDGET-LIMITED-CHECKPOINT`, or `FAILED`. Never translate a blocker to success or claim unrun tests/evals.

For each mutation state the invariant, make one reversible change, run its named test, confirm the intended failure, revert immediately, rerun, and scan residue. Before completion run fresh targeted/full evidence, inspect output/counts/logs, restore generated files, validate planning and skills, inspect diff/status, and update handoff without erasing domain context. Read [proof chain](references/runtime-proof-chain.md), [evidence matrix](references/test-evidence-matrix.md), [mutation protocol](references/mutation-proof-protocol.md), and [completion gate](references/completion-claim-gate.md).

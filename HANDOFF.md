# HANDOFF

## Zadanie

SEPA Nexus is a synthetic, deterministic SEPA/ISO 20022 payment-quality platform. This session
delivered the next autonomous backend tranche: source-backed money, finality, outbox-runtime and
egress-boundary evidence, while preserving the frozen ADRs and one-writer-per-schema rules.

## Zrobione

- Seven verified stories across six epics are complete and committed (no push):
  - `ab11dcc` closes EPIC-32 Story 32.5 ledger journal-currency/reversal integrity evidence.
  - `a9c3dbe` closes EPIC-40 Story 40.1 gross-instant insufficient-liquidity atomicity and records
    EPIC-36 Story 36.2 as source-blocked.
  - `87c0e0b` closes EPIC-18 Story 18.5 restricted outbox-relay runtime identity.
  - `d38f7d2` adds V44 and closes EPIC-33 Story 33.4: nullable payment timeout/revocation facts
    remain independent of business rejection and finality, with fresh and V43→V44 PostgreSQL 18
    migration proofs plus a mutation proof.
  - `38fe917` completes EPIC-07 Story 7.4 and EPIC-09 Story 9.5. The scheduled relay now retains
    per-relay operational failure truth; a PostgreSQL 18/Kafka runtime test revokes the real relay
    role's `SELECT`, observes DOWN/unpublished, restores the narrow grant, and observes real
    scheduled publication/UP. Scheduler/datasource ownership is structurally and runtime proven.
  - `9229b82` completes EPIC-14 Story 14.3 with `DeliveredNotFinalTest`: an `egress_role` delivery
    transition cannot establish or change payment finality. A temporary SECURITY DEFINER
    cross-schema mutation made the test fail, then was removed.
  - `4460586` records source/capability blockers for EPIC-29 Story 29.1, EPIC-33 Story 33.3 and
    EPIC-42 Story 42.1.
- Planning, capability-graph, story-inventory and all repository skill validators pass. The final
  two consecutive `./mvnw -f backend test` regressions passed cleanly; 102 Surefire suites report
  no failures or errors.
- The worktree is clean. Maven's generated `build/generated-spring-modulith/javadoc.json` was
  restored after test runs and is not part of any commit.

## Utknęliśmy na

No currently defensible READY story remains after the reserve audit. The next high-value work is
blocked by explicit missing contracts/capabilities:
- EPIC-33 Story 33.3: no ADR-N8 `payment.sla.breached` topic/payload/owner or source-backed timer
  threshold/policy.
- EPIC-42 Story 42.1: the case schema, `ReturnPaymentRequestPort`, and normal return-payment
  intake contract do not exist; do not invent a direct return, `RETURNED` transition, or reversal.
- EPIC-29 Story 29.1: no complete `iso.iso_outbound_artifacts` DDL or render-profile snapshot
  contract; EPIC-44 remains blocked on the unresolved profile representation.

## Plan na następny krok

Start by reading `planning/README.md`, `planning/BACKLOG-REDESIGN.md`, and
`planning/capabilities.yaml`, then re-audit the highest-ranked not-done candidate for a newly
source-backed READY capability before implementing anything.

## Pułapki, których nie wolno powtórzyć

- Preserve ADR-N9/N10/N11, five independent status axes, RLS, append-only ledger/finality
  evidence, and one-writer-per-schema; do not infer finality from delivery, receipt or ISO status.
- Do not invent the SLA Kafka contract, return/recall semantics, case request path, artifact DDL,
  or egress-profile representation; retain the recorded SOURCE/CAPABILITY blockers until a source
  or accepted decision supplies them.
- Test Maven runs rewrite `build/generated-spring-modulith/javadoc.json`; restore that generated
  artifact with `apply_patch` unless its change is explicitly reviewed and intended.
- Use `apply_patch` for file edits. Never push, reset, clean, or discard existing worktree changes.

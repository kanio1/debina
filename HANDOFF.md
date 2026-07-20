# HANDOFF

## Zadanie

SEPA Nexus is a synthetic, deterministic ISO 20022 payment-quality platform. Wave 3 reconciles the prior backend tranche, preserves active ISO/Kafka guidance, and delivers only source-backed capabilities.

## Zrobione

- Historical delivery at `ac9aa47` remains reconciled: seven verified backend stories around ledger integrity, gross-instant rejection/finality, outbox runtime, timeout facts, and egress/finality separation were not repeated.
- Revalidated unchanged blockers once: EPIC-33 Story 33.3 is `SOURCE-BLOCKED` (no ADR-N8 SLA Kafka contract); EPIC-42 Story 42.1 is `CAPABILITY-BLOCKED` (no case/requested-payment intake); EPIC-29 Story 29.1/EPIC-44 is `SOURCE-BLOCKED` (no complete outbound artifact/profile-snapshot representation).
- Implemented EPIC-51 Stories 51.1 and 51.2, pending commit: V45 adds the source-shaped `reference_data.profile_route_priorities` catalog; `RouteCandidateResolver` resolves only exact scheme/service/currency candidates, orders by configured priority, and filters `scheme_profiles` validity-window facts without making route, payment, settlement, money, finality, Kafka, or ISO decisions.
- PostgreSQL 18 fresh and V44→V45 upgrade proofs, owner/read/foreign writer grant proofs, duplicate-key proof, and a validity-filter mutation proof pass. Targeted suite: 6/0/0. Two full backend regressions pass: 446/0/0 each. Planning, capability and skill validators pass; generated story inventory was updated.

## Utknęliśmy na

No technical blocker for the verified EPIC-51 slice; it still needs final diff inspection and a coherent local commit. Remaining high-value stories include source gaps: the allowed-message-set model for EPIC-51 Story 51.3, reconciliation profile DDL for EPIC-57, and case-schema DDL for EPIC-65 are not specified precisely enough to infer.

## Plan na następny krok

Inspect the complete Wave 3 diff, commit the verified EPIC-51 routing slice, then continue the reserve-candidate audit from the durable program record.

## Pułapki, których nie wolno powtórzyć

- Never treat `payment.sla.breached`, return/reversal semantics, or outbound profile representation as authorized by an epic title; the source and Kafka catalog have not changed.
- Preserve the five status axes, one-writer-per-schema, `LedgerPort` boundary, and delivery-never-finality rule.
- Maven rewrites `build/generated-spring-modulith/javadoc.json`; restore it unless an intentional generated artifact change is reviewed.
- Keep `.claude/skills` as authoring source and `.agents/skills -> ../.claude/skills` as the discovery bridge; explicit skill invocation is not implicit-routing proof.

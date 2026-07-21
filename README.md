# SEPA Nexus — Architecture Decision Records Index

This directory holds the binding architecture decisions for the SEPA Nexus RegOps Platform. Every decision below is `Frozen` per the `sepa-nexus-decision-gate.md` — none may be reopened without a superseding ADR.

## Pre-existing frozen decisions (carried from the main blueprint and its integration patches, reaffirmed by the decision gate, not re-filed as separate ADR-N files here)

CPC-SP topology · one Spring Modulith deployable · one-writer-per-schema · strategy-by-`(settlement_basis, liquidity_mode)` never by CSM/profile name · `LedgerPort`-only money path · finality is an explicit, profile-configured rule; accepted/posted/delivered ≠ final · return-after-finality is a new opposite-direction payment, never a ledger reversal · egress owns transport state only, never finality · reconciliation is read-only detection-and-escalation, never repair · case is decision-and-coordination only · GraphQL is read-only in MVP; writes are REST/gRPC commands · simulation exercises only public paths (`csm.response.received` is its sole entry point) · PostgreSQL 18 baseline, PostgreSQL 19 lab-only profile · Maven (not Gradle) · selective RLS (tenant/evidence tables yes; queue/ledger tables no — ownership grants instead) · raw evidence archive never deduplicates · FSM-not-workflow (no BPMN/DMN engine).

## New decisions frozen by this patch pass

| ADR | Title | Closes |
|---|---|---|
| [ADR-N1](./ADR-N1-iteration-0-before-iteration-1.md) | Iteration 0 precedes Iteration 1 | R-01 / Blocker B1 |
| [ADR-N2](./ADR-N2-routing-in-process-for-mvp.md) | Routing is in-process for MVP; gRPC extraction is P2 | R-07 |
| [ADR-N3](./ADR-N3-nextjs-bff-token-model.md) | Next.js BFF token model | part of R-15 / Blocker B5 |
| [ADR-N4](./ADR-N4-sse-for-mvp-live-feeds.md) | SSE for MVP live feeds | R-24 |
| [ADR-N5](./ADR-N5-per-schema-outbox-inbox.md) | Per-schema outbox/inbox tables + shared dispatcher role | R-11 / Blocker B4 |
| [ADR-N6](./ADR-N6-one-priority-taxonomy.md) | One priority taxonomy (`[MVP]`=Iter 0–5, `[P1]`=wave 1, `[P2]`=wave 2/labs) | R-02 / R-05 |
| [ADR-N7](./ADR-N7-json-direct-pseudo-message-version.md) | `JSON_DIRECT` pseudo message-version | R-08 / Blocker B3 |
| [ADR-N8](./ADR-N8-asyncapi-topic-catalog-source-of-truth.md) | §3.7 v2 topic table as sole AsyncAPI source | R-10 |
| [ADR-N9](./ADR-N9-synthetic-credit-transfer-learning-platform-scope.md) | Synthetic Credit Transfer learning-platform scope and non-claims | Product-scope gate |
| [ADR-N10](./ADR-N10-settlement-finality-and-ledgerport-reservations.md) | Settlement finality authority and LedgerPort reservations | Finality/LedgerPort decision gate |
| [ADR-N11](./ADR-N11-gross-instant-single-transaction-security-definer.md) | Gross-instant single-transaction SECURITY DEFINER coordination | EPIC-33/36 transaction-boundary decision |
| [ADR-N12](./ADR-N12-routing-fallback-rule-identity.md) | Routing fallback-rule identity | EPIC-54 fallback evidence |
| [ADR-N13](./ADR-N13-deferred-cycle-representation-and-finality-trigger.md) | Deferred-cycle representation and finality trigger | EPIC-37/39 deferred settlement |
| [ADR-N14](./ADR-N14-keycloak-database-backup-and-realm-export.md) | Dedicated Keycloak database and offline realm backup | EPIC-74 Story 74.5 |
| [ADR-N15](./ADR-N15-enterprise-payment-processing-research-platform-scope.md) | Enterprise payment-processing research-platform scope | Supersedes ADR-N9 Playwright-first scope only |
| [ADR-N16](./ADR-N16-playwright-validation-sequencing.md) | Playwright validation sequencing | Minimal smoke early; full acceptance deferred |
| [ADR-N17](./ADR-N17-demand-driven-graphql-operational-read-models.md) | Demand-driven GraphQL operational reads | Clarifies ADR-W9 |

## Status legend

- **Frozen** — binding; requires a superseding ADR to change.
- **Accepted** — binding for now; may be revisited on new evidence without a full supersession (none currently in this set — all eight are Frozen).

## Still open (not frozen by this pass — tracked, not blocking)

- `[DECISION-NEEDS-EVIDENCE]` OSS ledger extraction as a standalone library — revisit only on evidence, not on a calendar.
- `[DECISION-NEEDS-EVIDENCE]` PG19 feature promotions (`ON CONFLICT DO SELECT`, `FOR PORTION OF`, `MERGE/SPLIT PARTITIONS`, `SQL/PGQ`) — revisit only on PostgreSQL 19 GA.
- `[DECISION-NEEDS-EVIDENCE]` Routing gRPC extraction go/no-go (the P2 exercise enabled by ADR-N2) — revisit once MVP is stable.
- `[DECISION-NEEDS-EVIDENCE]` BM25 (`pg_search`) adoption beyond the FTS baseline — revisit on demonstrated FTS ranking insufficiency; AGPL-v3 licensing constraint stands regardless.

## Remaining `[MVP-BLOCKER]`-class gaps not closed by ADR alone

Two blockers from the decision gate require a design document, not just an ADR:

- **B2 — `signature` module** has no schema/DDL/ports design yet. See the ownership-integration patch (this pass) for the boundary row; full module design is the next blueprint to produce.
- **B5 (remainder) — frontend screens, navigation, role→screen matrix** are not yet designed; ADR-N3 only fixes the token model. See "NEXT" in the patch summary.

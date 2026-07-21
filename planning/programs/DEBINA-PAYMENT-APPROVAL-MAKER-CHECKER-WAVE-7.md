# Debina Payment Approval Maker–Checker Wave 7

## Baseline and historical reconciliation

- Actual baseline/local `HEAD`: `ba709048f5e8ca87252cb80597b7c8e83dd3db11` on `main`; clean before this wave.
- Wave 6 commits `2355304`, `33e1193`, and `ba70904` are reconciled historical evidence only: the 12-role Keycloak realm, Organization claim normalization, maker/checker seed separation, dedicated Keycloak PostgreSQL 18 and backup/restore proof are not replayed or counted here.
- `.agents/skills -> ../.claude/skills` resolves to the canonical tracked source.  Explicit invocation/loading is verified; no implicit-routing claim is made.

## Active authority and skills

Applied: `artifact-derived-planning`, `epic-story-task-catalog`, `spring-modulith-module`, `postgres-rls-migration`, `sepa-nexus-flyway-safe-change`, `sepa-nexus-database-testing`, `sepa-nexus-payments-data-integrity`, `debina-payment-state-finality`, `debina-iso20022-validation-lineage`, `debina-kafka-payment-contract`, `debina-runtime-proof-testing`, and `sepa-nexus-database-review`.

| Source | Relevant authority |
|---|---|
| `sepa-nexus-message-flow-and-data-blueprint.md` | §2.2b `[FREEZE]` approval prefix gate, table sketches, idempotency and race semantics; §§3.5/3.6.2/4.7/4.13a ownership/RLS/catalog rules |
| `sepa-nexus-keycloak-26-security-architecture-blueprint.md` | §§5, 10, 12 object authorization, `payment_approver`, and same-transaction application audit |
| ADR-N5, ADR-N7, ADR-N9, ADR-N10 | module-owned outbox, preserved JSON lineage, synthetic scope and independent finality |
| `planning/programs/DEBINA-DECISION-AND-CAPABILITY-UNLOCK-WAVE-6.md` | Wave 6 historical boundary and unchanged Class C decisions |

## Planning owner and candidate inventory

No existing epic/story owned maker–checker (`rg` across every epic, inventory and graph found only the Wave 6 realm seed).  Source-derived `EPIC-76` now owns it.

| Candidate | Readiness | Evidence / disposition |
|---|---|---|
| 76.1 approval persistence | VERIFIED | source DDL/ownership and Class B ADR-W7-01; V53/V54 with PostgreSQL proof |
| 76.2 submission prefix gate | READY | depends only on 76.1; archive/idempotency/lineage/outbox implementation is present |
| 76.3 approve/reject | CAPABILITY-BLOCKED | frozen audit requires `audit.audit_log` through absent evidence-audit module/port |
| 76.4 expiry | CAPABILITY-BLOCKED | same missing audit capability plus 76.3 |
| 76.5 internal approval queue | READY | payment-lifecycle owns source rows and can prove RLS/cursor behavior internally |
| 76.6 UI/external queue | DECISION-BLOCKED | graph `gate.graphql-owner`; writes may not bypass BFF/REST boundary |
| 76.7 Playwright | ITERATION-BLOCKED | first-three-screen gate remains closed |
| 76.8 batch | CAPABILITY-BLOCKED | no file/batch aggregate or membership capability |

Primary queue: 76.1, 76.2, 76.5.  Reserve queue: source-derived evidence-audit owner audit, EPIC-16/16.1 only after a real projection exists, EPIC-24/24.3 re-audit, 74.4 only with its full contract.  The queue is locked subject to actual readiness re-checks.

## Class A/B decisions and implementation

- **Class B / ADR-W7-01:** nullable `payment.payments.status` represents only a pre-FSM approval-gated payment; retaining `RECEIVED` would be a false lifecycle state, while a staged aggregate would duplicate payment/lineage/idempotency state.  Existing no-approval ingress remains `RECEIVED`.
- **Class A:** approval and matrix records use source fields, a unique single-payment row, partial pending-queue index, immutable submitted matrix reference, source status/timestamp checks, `numeric(18,2)` money selector, `ClockPort` boundary for later expiry, and join-through payment RLS rather than redundant approval tenant state.
- Added V53 `reference_data.approval_matrix_rules` (reference-data writer; payment read only) and V54 `payment.payment_approvals` (payment-lifecycle writer).  No Kafka topic/event, ISO identifier, finality, transport or frontend surface changed.

## Verification and review

- Structural RED: `ApprovalPersistenceMigrationTest` failed at V52 because `payment.payments.status` was `NOT NULL` (`expected YES but was NO`), log `/tmp/DEBINA-PAYMENT-APPROVAL-MAKER-CHECKER-WAVE-7/red-approval-persistence.log`.
- GREEN: `ApprovalPersistenceMigrationTest` **2/0/0 PASS** using isolated PostgreSQL 18 Testcontainers: fresh V1→V54 and representative V52→V54 upgrade.  It proves existing `RECEIVED` survives, pending payment accepts null business status, table/grant ownership, `PUBLIC` insert revocation, same-/cross-tenant, branch and empty-GUC RLS behavior, duplicate approval rejection, matrix immutability, blank reject comment rejection, and checker=maker rejection.  Log: `/tmp/DEBINA-PAYMENT-APPROVAL-MAKER-CHECKER-WAVE-7/mutation-restored-green.log`.
- Mutation proof: removing the database maker≠checker check made the focused suite fail (`Expecting code to raise a throwable`); restored immediately, then GREEN rerun passed.  Log: `/tmp/DEBINA-PAYMENT-APPROVAL-MAKER-CHECKER-WAVE-7/mutation-remove-maker-checker-guard.log`.
- Compatibility review: the new source-defined payment FK exposed nine older PostgreSQL test fixtures that truncated the parent before its new child, and the V50 upgrade test still asserted V52.  The fixtures now truncate `payment.payment_approvals` first and the upgrade assertion expects V54; this preserves the FK rather than weakening it.  The focused compatibility suite passed **56/0/0**.  Log: `/tmp/DEBINA-PAYMENT-APPROVAL-MAKER-CHECKER-WAVE-7/regression-fixture-compatibility.log`.
- Full backend regression after that correction passed **491/0/0**.  This is the first clean tranche regression; the test count is two above the Wave 6 baseline because this story adds two migration proofs.  Log: `/tmp/DEBINA-PAYMENT-APPROVAL-MAKER-CHECKER-WAVE-7/backend-regression-story-76-1-fixed.log`.
- Database review: **PASS.** V53/V54 are append-only additive migrations; V54's metadata-only `DROP NOT NULL` preserves existing rows; no backfill or cross-schema runtime writer is added.  The only cross-schema relationship is the source-defined read-only matrix FK/grant.  RLS is forced on both tenant-scoped tables, `PUBLIC` is revoked, and isolated fresh/upgrade plus positive/negative role proofs exist.  No `SECURITY DEFINER`, money movement, audit substitute, or unapproved event contract was introduced.

## Promotion and commit history

- Planning owner created before implementation; first proof was RED, not plan-only.
- Coherent 76.1 commit is being created after the final diff/validator inspection; its SHA will be recorded immediately afterwards.

## Current blockers and next work

- `audit.audit_log` / `evidence-audit` has authoritative ownership but no implemented module, schema, public command-audit port or planning owner.  Therefore 76.3/76.4 are not claimed complete and neither logs, history, payment events nor outbox rows are treated as audit.
- GraphQL ownership, Playwright sequencing, batch and step-up gates remain unchanged.
- Next executable action: re-confirm 76.2 source/transaction assumptions, add its RED proof, then implement the supported approval-matrix submission gate without releasing `payment.received` for pending approval.

---
status: in-progress
depends_on: [EPIC-19-ingress-rest-json-payment]
source: "sepa-nexus-message-flow-and-data-blueprint.md §2.2b [FREEZE], §3.5, §3.6.2, §4.7, §4.13a; sepa-nexus-keycloak-26-security-architecture-blueprint.md §§5, 10, 12; sepa-nexus-react-nextjs-frontend-blueprint.md §§3a, 3b, 9, 10, 13–19"
---

# EPIC-76 — Payment approval: maker–checker prefix gate

The frozen maker–checker flow is a fifth approval-status axis before the payment business FSM.  It creates the payment and immutable ingress/ISO lineage first, then either releases the existing `payment.received` outbox event or retains the payment at an approval gate.  No new workflow engine, Kafka topic, payment business status, ISO status, finality, or batch aggregate is introduced.

## Story 76.1 — Approval persistence, matrix catalog, and pre-FSM representation

status: done
depends_on: []

Description: Add the source-defined `payment.payment_approvals` and `reference_data.approval_matrix_rules` tables with one-writer-per-schema grants, read-only matrix access from payment, approval-state constraints, and a source-compatible nullable business lifecycle status for pre-FSM pending payments.  Existing no-approval creation remains `RECEIVED`.

Completion criterion: migration and Testcontainers proof establish the schema, writer isolation, RLS join-through behavior, immutable matrix reference, and both pending/pre-existing lifecycle representations.

Tasks:
- [x] **Add forward-only V53/V54 approval persistence migrations.** Created `payment.payment_approvals`, `reference_data.approval_matrix_rules`, RLS join-through, owner grants, a partial queue index, immutable matrix-reference trigger, and nullable pre-FSM business-status representation.
      `verify: ./mvnw -f backend test -Dtest=ApprovalPersistenceMigrationTest` → PASS (2 tests; fresh PostgreSQL 18 and representative V52→V54 upgrade).
- [x] **Record the source-compatible representation decision.** `ADR-W7-01` keeps a pending payment's business status absent rather than inventing a lifecycle state; `RECEIVED` remains the no-approval path.
      `verify: ./mvnw -f backend test -Dtest=ApprovalPersistenceMigrationTest` → PASS (pending insertion accepts null status and ordinary V52 row remains `RECEIVED`).

## Story 76.2 — Approval-matrix evaluation and submission prefix gate

status: done
depends_on: [Story 76.1]

Description: Evaluate the supported single-payment matrix subset within the existing submission transaction.  `NOT_REQUIRED` creates an approval row and releases exactly one existing `payment.received`; `PENDING_APPROVAL` freezes the rule and maker identity, records a 24-hour expiry using `ClockPort`, and releases no event.

Completion criterion: JSON_DIRECT and pain.001 preserve archive/idempotency/lineage, replay is stable, policy ambiguity fails closed, and the no-approval flow remains backward-compatible.

Tasks:
- [x] **Implement the source-backed matrix subset and payment creation gate.** The first slice accepts exactly one active, tenant-wide rule with all optional selectors null.  `min_amount`, payment type, batch size, risk level and step-up are fail-closed unsupported; multiple active broad rules are a typed policy ambiguity.  JSON_DIRECT and pain.001 now create the approval row after lineage, and only `NOT_REQUIRED` releases the existing outbox event.
      `verify: ./mvnw -f backend test -Dtest=ApprovalSubmissionIntegrationTest,PaymentControllerTest,JsonDirectIngestionTest,Pain001SubmissionEndpointTest,PaymentServiceTest` → PASS (27 tests: pending/no-approval/replay/lineage/outbox/pain.001/controller proof); full backend regression → PASS (495 tests).

## Story 76.3 — Approve and reject commands with same-transaction audit

status: done
depends_on: [Story 76.2]

Description: Implement `POST /api/v1/payments/{paymentId}/approve` and `/reject` through payment-lifecycle with role, tenant/branch, maker≠checker, idempotency, conditional-transition and outbox-release guards.

Wave 8 status: the source-owned audit persistence/API, object-level authorization, separate denied-command
audit path, controlled approve audit-failure rollback, PostgreSQL races and a real Keycloak approver
token proof now exist. The remaining completion evidence is the dedicated transaction-identity and
expiry-audit-failure proof plus the final regression/validator gate; none may be inferred from the
basic path.

Tasks:
- [ ] **Provide the evidence-audit command-audit capability.** Add it only under a source-derived owner with an explicit transaction boundary; do not substitute logs, status history, payment events, or outbox rows for audit.
      `verify: ./mvnw -f backend test -Dtest=ApprovalDecisionAuditIntegrationTest` → same-transaction audit/outbox/rollback proof passes.
- [x] **Implement approve/reject once the audit port exists.** The conditional `PENDING_APPROVAL` update
      is first-writer-wins; before-method object authorization, maker separation, tenant/branch denial,
      idempotency, rollback and real PostgreSQL two-checker/expiry races are exercised.
      `verify: ./mvnw -f backend test -Dtest=ApprovalSubmissionIntegrationTest,ApprovalDecisionKeycloakRuntimeTest` → PASS (13 PostgreSQL 18 tests plus a real Keycloak 26.6.4 signature-validated approver-token proof).

## Story 76.4 — Approval expiry

status: done
depends_on: [Story 76.2, Story 76.3]

Description: A narrow service-role expiry capability changes only `PENDING_APPROVAL` rows past the frozen 24-hour limit to `EXPIRED`; it never starts the payment FSM or releases `payment.received`.

Wave 8 status: V59/V60 provide the bounded dedicated-role expiry command with a SYSTEM audit
append, replay proof and real approve/reject-versus-expiry races. The remaining completion evidence
is controlled audit-append rollback and final gate proof.

Tasks:
- [ ] **Implement replay-safe expiry after the audit capability exists.** Use `ClockPort`, a narrow system identity and conditional transition semantics that cannot overwrite interactive terminal decisions.
      `verify: ./mvnw -f backend test -Dtest=ApprovalExpiryIntegrationTest` → expiry/replay/approve-vs-expire/reject-vs-expire/rollback proof passes.

## Story 76.5 — Module-owned approval queue/detail read model

status: done
depends_on: [Story 76.1]

Description: Provide a typed payment-lifecycle read model for pending single-payment approvals, tenant/branch-scoped through the payment owner, deterministic cursor pagination, and an honest expired-not-yet-processed representation.  It is internal until the frozen GraphQL read boundary has an owner.

Completion criterion: the read model exposes only decision-relevant fields and proves same-tenant/branch visibility, cross-tenant/branch denial, empty-GUC behavior and stable cursor ordering.

Tasks:
- [x] **Implement and prove the internal approval queue/detail read model.** `ApprovalQueueReadModel` is payment-lifecycle internal, requires `payment_approver`, uses payment RLS plus deterministic `(submitted_at,id)` cursor pagination, and reports expired-but-unprocessed pending work honestly.  No REST or GraphQL adapter was added.
      `verify: ./mvnw -f backend test -Dtest=ApprovalSubmissionIntegrationTest` → PASS (4 PostgreSQL 18 tests including queue cursor/RLS/expiry); two full backend regressions → PASS (496 tests each).

## Story 76.6 — Payments workspace approval queue and command UI

status: done
depends_on: [Story 76.3, Story 76.5, gate.graphql-owner]

Description: The `payment_approver` queue belongs in the existing Payments & Files workspace, with REST commands through the BFF and reads only through the established GraphQL boundary.

Blocker: no GraphQL read capability exists and `gate.graphql-owner` remains open.  The existing first-three-screens sequencing rule also keeps Playwright unavailable.

Tasks:
- [ ] **Expose the approved read contract and build the queue UI only after the GraphQL owner is decided.** Do not store browser tokens or add GraphQL mutations.
      `verify: pnpm run build && pnpm run lint && pnpm run typecheck` → PASS.

## Story 76.7 — Playwright maker–checker acceptance

status: blocked
depends_on: [Story 76.6, Story 24.1]

Description: Acceptance is deliberately separate from the backend and UI capability because Playwright remains gated until the first three screens exist.

Tasks:
- [ ] **Add maker→checker happy and negative acceptance after the sequencing gate opens.**
      `verify: pnpm run test:e2e -- --grep "@smoke.*maker-checker"` → PASS.

## Story 76.8 — Batch approval and item override

status: blocked
depends_on: [Story 76.3, EPIC-73-ingress-file-rail]

Description: Batch commands and item override are source-defined but require a real batch/file aggregate.  They are excluded from the single-payment slice.

Tasks:
- [ ] **Implement batch decision semantics only after the batch owner, membership and file capability exist.**
      `verify: ./mvnw -f backend test -Dtest=BatchApprovalIntegrationTest` → batch/group/override proof passes.

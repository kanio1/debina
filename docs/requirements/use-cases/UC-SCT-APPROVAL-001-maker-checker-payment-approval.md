# UC-SCT-APPROVAL-001 — Maker-checker payment approval

**Status:** PILOT / implemented-project-policy evidence; **Business process:** BP-03 approval-and-release; **Level:** system use case.

## Goal, scope and actors

**Goal:** make one authorized, durable approval decision for a single submitted payment, releasing only the existing post-receipt path when approved. **Scope:** Debina's synthetic single-payment project policy; it is not an EPC/CSM approval process. **Primary actor:** payment approver. **Supporting actors:** payment submitter (maker), payment-lifecycle, evidence-audit, GraphQL/BFF operational-read adapters. **External systems:** Keycloak supplies the authenticated subject/roles; no payment rail is involved. **Trigger:** a payment's frozen approval-matrix decision requires approval.

## Preconditions and guarantees

Preconditions: payment/ingress/ISO lineage was persisted; approval matrix result is frozen; caller is authenticated and authorized for tenant/branch. Minimal guarantee: no terminal decision or release occurs on a denied, conflicting, expired, or failed command. Success guarantee: exactly one terminal decision and its required audit evidence persist; approval releases the existing `payment.received` path once, while rejection/expiry do not.

## Main success scenario

1. Submitter submits a single payment; payment-lifecycle creates the payment, ingress/ISO evidence, and an approval row.
2. If approval is required, the row is `PENDING_APPROVAL`; no release event is emitted.
3. Approver views the tenant/branch-scoped pending queue.
4. Approver sends an approve or reject command with an idempotency key.
5. payment-lifecycle authorizes the subject, rejects maker self-approval, and conditionally transitions the pending row.
6. In the required transaction boundary, evidence-audit appends the command audit entry.
7. Approval emits/reuses the existing received-path release once; rejection does not. The decision response is returned.

## Alternatives and failures

- A: matrix says no approval: create the approval representation and release the existing received path; do not enter the pending queue.
- B: queue/detail read is denied cross tenant/branch, missing role, or empty tenant context; reveal no foreign object information.
- C: maker equals checker: deny before a decision.
- D: approval is already terminal, expired, or loses a checker/expiry race: conditional update conflicts; no second terminal state or release.
- E: same idempotency request replays its stable result; changed payload conflicts.
- F: audit append fails: decision/release rollback. [PROJECT-SIMULATION] the exact project transaction implementation is authoritative; no rail assertion is made.

## Business rules and source references

Rules: BR-APPROVAL-001 through BR-APPROVAL-008 in `BUSINESS-RULE-CATALOG.yaml`. Source classification: `[PROJECT-ADR] project-adr-n10` and accepted project implementation/ADR evidence. BP-03 explicitly classifies approval as `project-policy`; no EPC, ISO, STEP2, RT1, TIPS or STET behaviour is asserted.

## Source references

`project-adr-n10` in `docs/standards/SOURCE-REGISTRY.yaml`; BP-03 in `BUSINESS-PROCESS-CATALOG.yaml`; EPIC-76, EPIC-77 and EPIC-78 implementation/test records are project evidence only, not higher-authority rail evidence.

## Security, ownership, state and messages

Security scenarios: QS-SEC-01 tenant/branch non-disclosure; role/maker-checker authorization evidence in EPIC-76. Data ownership: payment-lifecycle owns `payment.payment_approvals` and command transition; evidence-audit owns `audit.audit_log`; reference-data owns approval matrix rules; GraphQL and BFF own no domain state. State transitions: `PENDING_APPROVAL → APPROVED | REJECTED | EXPIRED`; no-approval stays on the existing `RECEIVED` path. Commands: submit, approve, reject, expire due approval. Queries: `ApprovalQueueQuery`, approval detail, audit trail. Events: existing `payment.received` releases only on no-approval/approved path; BP-03 catalogues `payment.approval.decided`. Transaction boundary: decision, required audit append, and release effect are one project command boundary.

## Quality, observability and architecture realization

Quality: QS-INT-01 idempotency, QS-SEC-01 isolation, QS-TRC-01 decision investigation, QS-REL-01 durable release/outbox recovery. Observe payment/approval IDs, command audit, immutable ingress/ISO lineage and source-owned audit/read queries. Architecture: payment-lifecycle domain module and payment schema own commands/approval state; evidence-audit supporting module owns append-only audit; source-owned query port feeds Query-only GraphQL; BFF is an authenticated technical adapter. Outcome: `CURRENT-ARCHITECTURE-SUFFICIENT`; no new module, aggregate, context or ADR is introduced.

## Slices and Example Mapping

| Slice | Outcome / trace | Rules | Examples / questions / out-of-scope |
|---|---|---|---|
| UCS-SCT-APPROVAL-001-A | submit when approval is not required; EPIC-76 76.2 | BR-006, BR-008 | Example: one release. Out: batch/file. |
| UCS-SCT-APPROVAL-001-B | submit requiring approval; EPIC-76 76.1–76.2 | BR-002 | Example: pending creates no release. Question: rule selectors beyond supported subset. |
| UCS-SCT-APPROVAL-001-C | view pending approval; EPIC-76 76.5–76.6 | BR-002 | Examples: cross-tenant/branch and missing-role denied; expired-but-unprocessed visible honestly. |
| UCS-SCT-APPROVAL-001-D | approve pending payment; EPIC-76 76.3 | BR-001, BR-002, BR-005, BR-006, BR-008 | Examples: maker differs, replay same key, changed payload. |
| UCS-SCT-APPROVAL-001-E | reject pending payment; EPIC-76 76.3 | BR-001, BR-002, BR-005, BR-007, BR-008 | Example: rejection never releases. |
| UCS-SCT-APPROVAL-001-F | expire pending approval; EPIC-76 76.4 | BR-003, BR-005 | Examples: approval/reject versus expiry race. |
| UCS-SCT-APPROVAL-001-G | concurrent checker decisions; EPIC-76 76.3–76.4 | BR-002, BR-004, BR-008 | Examples: two checkers race; first terminal decision wins. |
| UCS-SCT-APPROVAL-001-H | deny maker self-approval; EPIC-76 76.3 | BR-001 | Example: same subject is denied and audited where supported. |
| UCS-SCT-APPROVAL-001-I | investigate decision through audit/evidence; EPIC-76 76.3, EPIC-77 | BR-005 | Examples: audit append failure rollback; trace payment to audit/lineage. |
| UCS-SCT-APPROVAL-001-J | existing UI/BFF path; EPIC-76 76.6, EPIC-78 | BR-001, BR-002 | Existing Query-only GraphQL/BFF evidence; full UI acceptance remains gated. |
| UCS-SCT-APPROVAL-001-K | future full acceptance automation; EPIC-76 76.7 | — | Out-of-scope until ADR-N16 sequencing gate opens. |
| UCS-SCT-APPROVAL-001-L | future batch/item approval; EPIC-76 76.8 | — | Excluded until a source-backed file/group aggregate exists. |

## Test strategy, gaps and questions

Use focused PostgreSQL/Testcontainers approvals, expiry, audit, Keycloak and GraphQL runtime tests named in EPIC-76; operational acceptance automation is deliberately future. [OPEN-QUESTION] approval matrix policy beyond the supported single broad-rule subset. [PARTICIPANT-DOCUMENTATION-REQUIRED] no participant/rail approval policy is modeled. Known gap: batch/item approval needs a file/group aggregate and is excluded.

# UC-SCT-APPROVAL-001 — Maker-checker payment approval

**Status:** PILOT / implemented-project-policy evidence; **Business process:** BP-03 approval-and-release; **Level:** user goal; **System of interest:** Debina; **System boundary:** Debina payment-processing system; **Primary actor type:** human_role; **Actor goal:** decide a pending payment approval; **Detail profile:** FULLY_DRESSED; **Discovery:** AI_DRAFT / NOT_REVIEWED; **Material questions open:** true; **Architecture evaluation:** ATAM_INSPIRED_DESK_REVIEW.

## Goal, scope and actors

**Goal:** make one authorized, durable approval decision for a pending single payment, releasing only the existing post-receipt path when approved. **Scope:** Debina's synthetic single-payment project policy; it is not an EPC/CSM approval process. **Primary actor:** payment approver. **Supporting external actor:** payment submitter (maker). **External system:** Keycloak supplies identity/roles; no payment rail is involved. Internal payment-lifecycle, evidence-audit, GraphQL and BFF belong to architecture realization, not actors. **Trigger:** the approver selects a pending approval.

## Preconditions and guarantees

Preconditions: UC-SCT-001 or an equivalent accepted submission created payment/ingress/ISO lineage; approval is `PENDING_APPROVAL`; matrix result is frozen; caller is authenticated/authorized for tenant/branch. Minimal guarantee: no terminal decision or release occurs on a denied, conflicting, expired, or failed command. Success guarantee: exactly one terminal decision and required audit evidence persist; approval releases `payment.received` once; rejection/expiry do not.

## Main success scenario

BF-1. The approver opens the pending approval queue.
BF-2. Debina returns only approvals visible to the approver's tenant and branch.
BF-3. The approver selects one pending payment and submits an approve or reject decision with an idempotency key.
BF-4. Debina authorizes the approver, verifies maker/checker separation, and conditionally records the terminal decision.
BF-5. Debina appends the required command audit evidence in the same project command boundary.
BF-6. For approval, Debina releases the existing received path once; for rejection it releases nothing.
BF-7. Debina returns the durable decision and correlation identifiers.

## Alternatives and failures

AF-2A — denied visibility. At BF-2, when tenant/branch/role is not authorized: Debina reveals no object existence; terminate with minimal guarantee.
CF-4A — maker self-approval. At BF-4, when maker equals checker: Debina denies before decision; terminate with minimal guarantee.
CF-4B — terminal/expired/race. At BF-4, when approval is terminal, expired or loses a race: Debina returns conflict; no second terminal state/release; terminate.
AF-3A — idempotent replay. At BF-3, equivalent key returns original outcome; changed payload conflicts; rejoin BF-7 or terminate.
FF-5A — audit append failure. At BF-5, Debina rolls back decision/release; terminate with minimal guarantee. `[PROJECT_SIMULATION]` transaction mechanics are project policy, not rail behavior.

## Business rules and source references

Rules: BR-APPROVAL-001 through BR-APPROVAL-008 in `BUSINESS-RULE-CATALOG.yaml`. Source classification: `[PROJECT-ADR] project-adr-n10` and accepted project implementation/ADR evidence. BP-03 explicitly classifies approval as `project-policy`; no EPC, ISO, STEP2, RT1, TIPS or STET behaviour is asserted.

## Source references

`project-adr-n10` in `docs/standards/SOURCE-REGISTRY.yaml`; BP-03 in `BUSINESS-PROCESS-CATALOG.yaml`; EPIC-76, EPIC-77 and EPIC-78 implementation/test records are project evidence only, not higher-authority rail evidence.

## Security, ownership, state and messages

Security scenarios: QS-SEC-01 tenant/branch non-disclosure; role/maker-checker authorization evidence in EPIC-76. Data ownership: payment-lifecycle owns `payment.payment_approvals` and command transition; evidence-audit owns `audit.audit_log`; reference-data owns approval matrix rules; GraphQL and BFF own no domain state. State transitions: `PENDING_APPROVAL → APPROVED | REJECTED | EXPIRED`; no-approval stays on the existing `RECEIVED` path. Commands: submit, approve, reject, expire due approval. Queries: `ApprovalQueueQuery`, approval detail, audit trail. Events: existing `payment.received` releases only on no-approval/approved path; BP-03 catalogues `payment.approval.decided`. Transaction boundary: decision, required audit append, and release effect are one project command boundary.

## Quality, observability and architecture realization

Quality: QS-INT-01 idempotency, QS-SEC-01 isolation, QS-TRC-01 decision investigation, QS-REL-01 durable release/outbox recovery. Observe payment/approval IDs, command audit, immutable ingress/ISO lineage and source-owned audit/read queries. Architecture: payment-lifecycle domain module and payment schema own commands/approval state; evidence-audit supporting module owns append-only audit; source-owned query port feeds Query-only GraphQL; BFF is an authenticated technical adapter. Outcome: `CURRENT_ARCHITECTURE_SUFFICIENT`; no new module, aggregate, context or ADR is introduced.

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
| UCS-SCT-APPROVAL-001-J | **Retired as slice:** architecture realization | — | See slice-audit migration; UI/BFF is not a behavioral slice. |
| UCS-SCT-APPROVAL-001-K | **Retired as slice:** test work | — | See slice-audit migration; acceptance automation is verification, not behavior. |
| UCS-SCT-APPROVAL-001-L | **Reclassified:** separate-use-case candidate | — | See UC-SCT-003 and slice-audit migration; file/group evidence is required. |

## Test strategy, gaps and questions

Use focused PostgreSQL/Testcontainers approvals, expiry, audit, Keycloak and GraphQL runtime tests named in EPIC-76; operational acceptance automation is deliberately future. [OPEN-QUESTION] approval matrix policy beyond the supported single broad-rule subset. [PARTICIPANT_DOCUMENTATION_REQUIRED] no participant/rail approval policy is modeled. Known gap: batch/item approval needs a file/group aggregate and is excluded.

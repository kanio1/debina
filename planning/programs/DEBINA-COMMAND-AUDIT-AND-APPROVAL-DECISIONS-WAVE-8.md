# Debina Command Audit and Approval Decisions — Wave 8

## Baseline and reconciliation

- Actual baseline: `edf6dcb14b5fe5bf36f3fa5a32b42bdb810aa679` on `main`, clean before Wave 8.
- Wave 7 commits `d87bb44`, `26d4a9c`, `39621a6`, `edf6dcb` were reconciled as historical evidence:
  V53/V54, prefix gate, queue and the pre-FSM approval axis are preserved and not counted again.
- Codex skill bridge remains canonical: `.agents/skills -> ../.claude/skills`.

## Binding source record

| Source | Relevant authority | Blob at baseline |
|---|---|---|
| `README.md` | frozen ADR index and one-writer/five-axis baseline | `d575e449903c727af7e4f7dfad05ff27be4a6d73` |
| `sepa-nexus-message-flow-and-data-blueprint.md` §§2.2, 2.2b, 3.2, 3.5-3.6, 4.7, 7.2 | approval decisions/expiry, evidence-audit owner, worker identity and RLS | `f8667131109858da5ff7f3d3a92d74d31a1df900` |
| `sepa-nexus-blueprint-ownership-integration.md` §3.6 | module ownership, ports and architectural tests | `fc88d643a0e5c24e73f3f5cb32a2056ab646428b` |
| `sepa-nexus-keycloak-26-security-architecture-blueprint.md` §§3,6,9-13 | object authorization, RLS and same-transaction application audit | `4f7250d967d3bd2369ce70f5889df6965e78e35c` |
| `planning/epics/EPIC-76-payment-approval-maker-checker.md` | Wave 7 boundary and 76.3/76.4 delivery contract | `479543e0c7db500cffe6b88c420bcddb3a449a98` |

ADR-N3/N5/N7/N9/N10/N11/N14 and ADR-W7-01 were read as binding context; no prior ADR supplies a
general audit boundary. ADR-W8-01 is the accepted focused Class B decision.

## Planning owner and queues

- New source-derived owner: `EPIC-77`, Stories 77.1-77.3; 77.1 is active.
- Primary queue: 77.1/77.2 audit boundary, EPIC-76 Story 76.3 approve/reject, EPIC-76 Story 76.4 expiry.
- Conditional reserve: 77.3 internal audit query/integrity proof.
- Excluded unchanged: GraphQL/UI, Playwright, batch, step-up, fraud/VoP and new Kafka contracts.

## Transaction, ownership and migration decision

- `evidence-audit` owns the new `audit` schema and only `audit.audit_log` in this wave.
- V55 introduces a NOLOGIN audit function owner and an append-only RLS table.
- Successful audit appends use the evidence-audit-owned typed `SECURITY DEFINER` function through
  the caller's existing transaction-bound connection. No direct payment DML on audit, no event,
  no after-commit listener and no second transaction.
- V58 adds a separate evidence-audit-owned `append_denied_command_audit` SECURITY DEFINER
  boundary. It uses an independent transaction because no domain mutation exists; its failure
  surfaces as a technical error and does not authorize the request.

## Evidence

- RED: `CommandAuditMigrationTest` reached V54 on isolated PostgreSQL 18 and failed as expected:
  `expected: "audit.audit_log" but was: null`.
  Log: `/tmp/DEBINA-COMMAND-AUDIT-AND-APPROVAL-DECISIONS-WAVE-8/red-command-audit-migration.log`.
- GREEN Story 77.1: `CommandAuditMigrationTest`, `CommandAuditArchitectureTest` and
  `ModularityTest` pass **5/0/0**. Fresh PostgreSQL 18 applies V1→V57; representative V54 rows
  survive V55-V57. RLS/grant proof covers tenant/branch isolation, empty context, narrow auditor
  read-only override, `PUBLIC EXECUTE` denial, direct mutation denial and canonical JSONB.
  Log: `/tmp/DEBINA-COMMAND-AUDIT-AND-APPROVAL-DECISIONS-WAVE-8/audit-slice-focused-green.log`.
- Mutation, successful-command transaction, denial, approval/race, expiry, Keycloak and final
  regression evidence: pending.
- Partial 77.2/76.3 GREEN (not story completion): `ApprovalSubmissionIntegrationTest` **6/0/0**
  proves the before-method `AuthorizationManager`, conditional first-writer-wins decision update,
  one approve outbox/audit row, reject outbox suppression, stable idempotent replay, and a denied
  submitter attempt with a separate `DENIED` audit row and no idempotency/domain mutation. Logs:
  `approval-decision-conditional-green.log`, `denied-audit-red-green.log`.

## Current checkpoint

Commit `73501af` contains Story 77.1. The next checkpoint is the focused 77.2/76.3 implementation
slice. Remaining proof is controlled audit failure, foreign tenant/branch/maker denial, HTTP/real
Keycloak, exact transaction identity and decision races; expiry has not started.

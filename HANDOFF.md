# HANDOFF

## Current objective

Deliver educational E1 for `UC-SCT-002` (signed single-instruction pain.001
intake) using production-grade thinking with an educational-grade process:
classify → plan → one implementation tranche → focused tests → one review →
one coherent commit.

## Current use case

`UC-SCT-002` / `UCS-SCT-002-A` / `UCS-SCT-002-B` (customer pain.001 submission).
Do not modify canonical use-case text unless a consciously accepted clarification
is required after reconcile.

## Current state

- Delivery process simplified: operating-model rules and PM/BA/TA/SM role skills
  are in place; Phase E governance is narrowed to Phase E review/planning paths.
- Working tree may contain draft E1 backend/mapping/signature/idempotency work
  that has **not** yet been committed through the new flow.
- No claim of production readiness, EPC TVS closure, or regulatory conformance.

## Completed

- Educational operating model documented in `.cursor/rules/00-project-operating-model.mdc`
  and related planning/review/handoff rules.
- Role skills: `product-manager`, `business-analyst`, `technical-architect`,
  `scrum-master`; BA integrates existing `enterprise-use-case-engineering`.
- Phase E rule limited to Phase E artifact paths; ordinary implementation no
  longer depends on review councils, approval queues, or admission records.

## Decisions

Educational directions for E1 (local/non-production; not production closure):

- Channel direction: `REST_PLUS_BFF_UPLOAD` (backend owns XML command; BFF is
  session-aware upload adapter). GraphQL mutation rejected for commands.
- Signature direction: versioned project profile `DEBINA-E1-DETACHED-ED25519-V1`
  (covered bytes, base64, outcomes). HTTP header/signer UUID transport remain
  implementation evidence only — not a frozen public contract.
- Validation direction: ISO XSD + EPC TVS + project business rules when lawfully
  available; E1 local work may proceed with ISO/project checks while TVS stays
  `BLOCKED_FOR_PRODUCTION`.
- Field scope: minimal single `PmtInf` / single `CdtTrfTxInf`; `NbOfTxs=1`,
  `PmtMtd=TRF`, `CtrlSum` consistency as VALIDATE_ONLY.
- CreDtTm: source message creation time distinct from record/receive time
  (`source_message_created_at` vs `recorded_at`); do not treat legacy
  `cre_dt_tm` as source CreDtTm.
- Architecture: current modular monolith sufficient for E1; no new aggregate,
  module, or Kafka topic for this tranche.
- Flyway: global versioning — do not reuse proposal numbers such as `V22`.

## Blocked for production

- Dated specialist approvals / review councils / canonical migration admission
- EPC TVS lawful acquisition, checksums and production validation claim
- Normative detached-signature transport contract and signer-identity authority
- XML encoding/charset acceptance profile
- Evidence retention, encryption-at-rest and legal hold
- Measured production intake limits acceptance
- External ISO/EPC conformance or settlement/clearing claims

These are `BLOCKED_FOR_PRODUCTION` and must not block safe local educational
implementation.

## Next tasks

1. Inspect and plan CreDtTm persistence semantics (source vs recorded timestamps,
   migration/expand-contract, mapper/lineage touchpoints).
2. Implement the minimal approved E1 mapping (VALIDATE_ONLY + MAP_AND_PERSIST
   fields only; no silent scope expansion).
3. Implement the detached Ed25519 verification boundary
   (`DEBINA-E1-DETACHED-ED25519-V1` outcomes; do not freeze HTTP headers).
4. Implement tenant-scoped idempotency (opaque key, same-payload replay,
   conflict on payload mismatch).
5. Perform one focused implementation review (BLOCKER/HIGH before commit).

## Resume from here

Start next task 1: inspect current CreDtTm / timestamp persistence and write a
concise Technical Architect plan (≤10 steps + verify commands) before further
implementation or commit.

# HANDOFF

## Current objective

Prepare the E1 backend tranche for one coherent commit after final review fixes.

## Current use case

`UC-SCT-002` / `UCS-SCT-002-A` / `UCS-SCT-002-B` (customer pain.001 submission).

## Current state

- E1 backend tranche corrections applied in working tree (not committed).
- Focused E1 regression green (93 tests).
- Use-case traceability validator: 0 errors (legacy warnings only).
- `git diff --check`: clean on changed paths.
- Nothing staged.

## Completed

- CreDtTm persistence closure (`source_message_created_at`, `recorded_at`, V61).
- Minimal E1 field mapping: MAP_AND_PERSIST + VALIDATE_ONLY per
  E1-FIELD-SCOPE-DECISION; deferred fields not promoted.
- Detached Ed25519 verification boundary (`DEBINA-E1-DETACHED-ED25519-V1`;
  archive → verify → parse ordering).
- Tenant-scoped idempotency with frozen submission HTTP outcome on replay
  (`response_code` from `ingress.idempotency_keys`; 202 body frozen as
  `PENDING_APPROVAL`).
- Review-correction tranche: mapping summary arithmetic, post-approval replay
  proof, JSON-direct frozen replay assertions.
- **HIGH fix:** unsupported stored idempotency `response_code` values fail with
  `IdempotencyDataCorruptionException` → HTTP 500 idempotency data integrity
  error; corruption integration test added.

## Decisions

Educational directions for E1 (local/non-production; not production closure):

- Channel direction: `REST_PLUS_BFF_UPLOAD` (backend owns XML command; BFF is
  session-aware upload adapter).
- Signature direction: versioned project profile `DEBINA-E1-DETACHED-ED25519-V1`.
  Inactive/expired/revoked keys currently collapse to `UNKNOWN_SIGNER`;
  `EXPIRED_KEY`/`REVOKED_KEY` not independently emitted yet.
- Field scope: minimal single `PmtInf` / single `CdtTrfTxInf`; `NbOfTxs=1`,
  `PmtMtd=TRF`, `CtrlSum` consistency as VALIDATE_ONLY.
- CreDtTm: `GrpHdr/CreDtTm` → `source_message_created_at`; Debina record time →
  `recorded_at`; offset-less `CreDtTm` interpreted as UTC (project interpretation).
- Idempotency: PostgreSQL `(source_id, idem_key)` with payload SHA-256; claim
  only after successful verify+map; replay uses stored `response_code` (201/202
  only; corrupt values are server-side integrity failures).
- Architecture: current modular monolith sufficient for E1.

## Blocked for production

- Dated specialist approvals / review councils / canonical migration admission
- EPC TVS lawful acquisition, checksums and production validation claim
- Normative detached-signature transport contract and signer-identity authority
- Signer registry is participant/global, not tenant-scoped; a valid participant
  key may be recognised across authenticated tenants (signature possession still
  required); tenant-bound signer authority unresolved
- IBAN check-digit, SCT EUR-only and UETR UUIDv4 syntax enforcement deferred
- XML encoding/charset acceptance profile
- Evidence retention, encryption-at-rest and legal hold
- Measured production intake limits acceptance
- External ISO/EPC conformance or settlement/clearing claims
- GraphQL/REST exposure of source/receive/record timestamps (deferred)
- iso/ingress RLS on evidence tables (pre-existing gap)
- Contract-phase drop/rename of legacy `cre_dt_tm` column
- Idempotency TTL/retention/archival policy
- Distinct `EXPIRED_KEY` / `REVOKED_KEY` profile outcomes

These are `BLOCKED_FOR_PRODUCTION` and must not block safe local educational
implementation.

## Next tasks

1. Narrow verification pass and one coherent E1 commit (exclude CLAUDE files,
   `.cursor/**`, generated javadoc).
2. Plan the BFF and accepted-path Playwright tranche (out of current scope).

## Resume from here

Stage E1 backend + docs only; commit when ready.

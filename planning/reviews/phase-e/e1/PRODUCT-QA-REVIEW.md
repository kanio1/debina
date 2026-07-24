# Product and QA review — E1

Review state: `NOT_REVIEWED`

Reviewer role: Product Manager/Owner and QA/SDET test architect

Approval evidence: `PENDING_DATED_HUMAN_RECORD`

## Exact review scope

Review the vertical actor outcome and risk-based test allocation for
`UC-SCT-002`, including proposed `EPIC-24/24.10`. No tests or UI are
implemented in this planning run.

## Documents and sections inspected

- Phase E cohort/story contracts, frontend reconciliation and E1 decision pack.
- `UC-SCT-002`, `BR-SCT-003`, E1 field map and source evidence.
- `SEPA-PAYMENT-TEST-ALLOCATION-MATRIX.md`, ADR-N16/N17, Dagger architecture,
  existing REST/GraphQL/BFF/UI and runtime smoke evidence.

## Claims being approved

- One vertical story owns authenticated upload, deterministic validation/
  provenance result, and link to existing detail/evidence.
- REST remains command owner; BFF adapts session/security; GraphQL remains
  query-only; frontend owns no payment semantics.
- Validation breadth belongs below the UI; Playwright proves only wiring and
  actor journey.

## Project decisions proposed

Place the future story in existing frontend epic as `EPIC-24/24.10`.
Allocate tests as follows:

- source/schema: ISO XSD/EPC TVS selection and checksum/profile drift;
- unit/property: field boundaries, identifiers, amount/currency, IBAN and
  control-sum arithmetic;
- database: migrations, ownership, RLS, lineage, constraints and idempotency;
- integration: archive → signature → XML/profile validation → mapping →
  persistence;
- BFF contract/component: session, role, CSRF/origin, content type/body,
  correlation and safe error forwarding;
- Dagger: one composed acceptance using real dependencies for the accepted journey;
- Playwright: exactly one login → upload → deterministic accepted result → payment detail
  smoke for the accepted journey only;
- Invalid signature: primary Spring/security integration test; optional secondary
  Dagger composed rejection proof; no E1 Playwright negative smoke.

## Open questions

- Which accepted/rejected copy and safe field visibility are understandable
  without exposing sensitive evidence?
- Is approval part of the one smoke or excluded to prevent duplicate maker-
  checker coverage?
- Which secondary tests prove a distinct risk rather than repeating mapper
  cases?
- Which executable verify names remain stable after implementation planning?

## Rejected alternatives

Separate backend/BFF/UI/Playwright stories for the small journey; browser
matrix, visual regression, every validation case in UI, GraphQL mutation,
frontend-only security, or Dagger claims without execution.

## Affected use case, slices and stories

`UC-SCT-002`; `UCS-SCT-002-A`, `UCS-SCT-002-B`; all five existing E1
stories and proposed `EPIC-24/24.10`.

## Council recommendations (AI_DRAFT)

Council date: 2026-07-24. Review state remains `NOT_REVIEWED`.

- User-visible outcomes: success, invalid-signature, validation-rejected,
  duplicate/idempotent, unauthorized — safe messaging without raw evidence.
- Approval excluded from E1 smoke.
- Test allocation unchanged per SEPA-PAYMENT-TEST-ALLOCATION-MATRIX; exactly one
  Playwright smoke — login → upload → deterministic accepted result → payment detail —
  for the accepted journey; no E1 Playwright negative smoke for invalid signature.
- Verify targets: `Pain001SubmissionEndpointTest` (existing);
  `pnpm run test:pain001-upload-route`, `dagger call smoke-signed-pain001`,
  `e2e/e1-signed-pain001.spec.ts` — all `PLANNED_NOT_IMPLEMENTED`.

## Blocking consequences

Until product and QA reviewers approve the outcome, copy, non-goals,
allocation and executable verifies, the future story remains
`DECISION_BLOCKED` with `candidate_after_gates: READY`.

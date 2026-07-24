# Payments domain review — E1

Review state: `NOT_REVIEWED`

Reviewer role: SEPA payments domain specialist and Product Owner

Approval evidence: `PENDING_DATED_HUMAN_RECORD`

## Exact review scope

Approve or request changes to the actor goal and terminology for `UC-SCT-002`
and slices `UCS-SCT-002-A`/`UCS-SCT-002-B`. The scope ends after a durable
validation/provenance outcome and existing payment detail/evidence read; it
does not claim clearing, settlement, finality, return/recall, SDD or regulatory
compliance.

## Documents and sections inspected

- `UC-SCT-002-submit-signed-pain001.md`, `BP-01`, and `BR-SCT-003`.
- EPC132-08 sections 1.1–1.7 and 2.1/2.1.1; message rows for group,
  payment-information and transaction concepts.
- Phase E lifecycle, reconciliation proposal, decision register and field map.
- Accepted ADRs governing one-writer schemas, maker-checker project policy,
  separate status axes, REST commands and query-only GraphQL.

## Claims being approved

- The primary actor is an authenticated payment submitter seeking a durable
  single SCT instruction outcome.
- Envelope/submission, ISO message, payment-information group, customer
  instruction, business payment order and payment transaction remain distinct.
- Approval is an optional Debina prefix policy after intake/lineage, not an EPC
  requirement.
- Accepted upload may link to the existing payment detail; rejection preserves
  safe provenance without inventing a payment/settlement outcome.

## Project decisions proposed

Admit only one `PmtInf` and one `CdtTrfTxInf` for E1 after profile review;
defer file/group/batch behavior to `UC-SCT-003`. Use REST plus BFF upload and
keep all route/rail/settlement behavior out of this cohort.

## Open questions

- Is the business outcome "submitted instruction" or "accepted payment order"
  at each validation boundary?
- Which validation failures may create only evidence versus a visible rejected
  submission record?
- Does requested execution date belong in the minimal admitted E1 outcome?
- Is approval required for the smoke path, optional, or explicitly excluded?

## Rejected alternatives

Treating one XML document as a payment aggregate; treating upload as clearing
acceptance; claiming maker-checker as scheme law; expanding E1 to multi-item
partial processing.

## Affected use case, slices and stories

`UC-SCT-002`; `UCS-SCT-002-A`, `UCS-SCT-002-B`; `EPIC-31/31.2`,
`EPIC-19/19.2`, `EPIC-19/19.4`, `EPIC-26/26.3`, `EPIC-26/26.4`, proposed
`EPIC-24/24.10`.

## Blocking consequences

Without approval, actor/goal semantics and the point at which a payment exists
remain `HUMAN_REVIEW_REQUIRED`; no canonical story can become `ENFORCED` or
`READY`.

# Database and field-mapping review — E1

Review state: `NOT_REVIEWED`

Reviewer role: PostgreSQL data owner, RLS and payment data-integrity reviewer

Approval evidence: `PENDING_DATED_HUMAN_RECORD`

## Exact review scope

Review the 28-row E1 mapping from external XML/evidence to canonical concepts,
module-owned schemas and operational reads for `UC-SCT-002`. This review does
not authorise migrations or a new aggregate.

## Documents and sections inspected

- E1 field map, mapping method/template and source evidence.
- Current mapper/command/lineage/persistence/signature code.
- Migrations `payment/V3`, `ingress/V10`, `iso/V11/V15/V21`,
  `signature/V13/V14`, ownership/RLS blueprints and ADR-N1/N2/N8/N10.
- EPC132-08 relevant field rows and EPC153-22 address guidance.

## Claims being approved

- Raw external structure, canonical concept, domain value, immutable evidence,
  database owner and read model are separate decisions.
- `MsgId`, `PmtInfId`, `InstrId`, `EndToEndId`, UETR and later `TxId` are not
  interchangeable.
- Raw payload hash is evidence, not a unique dedupe key; idempotency has a
  separate source/key/request-hash contract.
- `payment`, `ingress`, `iso` and `signature` retain one-writer ownership and
  fail-closed tenant access.

## Project decisions proposed

Retain exact raw bytes plus SHA-256 for E1 under reviewed controls; preserve
source timestamp separately from receive/record time; admit no File/Group/
Batch aggregate; add no JSONB/XML shortcut without query/retention evidence.

## Open questions

- The current writer stores receive time in `iso.iso_messages.cre_dt_tm`
  instead of parsing `GrpHdr/CreDtTm`; approve the correction design and
  compatibility semantics.
- Should E1 persist validation facts for counts/control sums, or only immutable
  result evidence?
- What uniqueness scope/indexes apply to each identifier?
- What RLS policy protects raw/signature evidence, including cross-schema
  linkage and auditor access?
- What encryption, masking, retention, archive/delete and legal-hold rules
  apply?

## Rejected alternatives

One column per XML element, XML nouns as aggregates, global identifier
uniqueness, payload-hash dedupe, direct cross-schema writes, GraphQL ownership,
partitioning without measured need.

## Affected use case, slices and stories

`UC-SCT-002`; `UCS-SCT-002-A`, `UCS-SCT-002-B`; `EPIC-19/19.2`,
`EPIC-19/19.4`, `EPIC-26/26.3`, `EPIC-26/26.4`, proposed
`EPIC-24/24.10`.

## Blocking consequences

The field map remains `NOT_REVIEWED`; schema/mapper changes and canonical story
migration remain unauthorised. Any database implementation must later use
Flyway impact analysis, Testcontainers-first RLS/ownership/idempotency tests
and an independent database review.

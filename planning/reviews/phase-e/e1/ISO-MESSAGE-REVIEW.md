# ISO message and EPC profile review — E1

Review state: `NOT_REVIEWED`

Reviewer role: ISO 20022 and EPC implementation-guideline specialist

Approval evidence: `PENDING_DATED_HUMAN_RECORD`

## Exact review scope

Review only `pain.001.001.09` under the current SCT C2PSP profile for
`UC-SCT-002`/`UCS-SCT-002-A`; review the safe result facts exposed by
`UCS-SCT-002-B`. No inter-PSP or CSM message profile is approved here.

## Documents and sections inspected

- EPC132-08 / 2025 v1.0: document information, 1.1–1.7, 2.1/2.1.1,
  rows for `MsgId`, `CreDtTm`, `NbOfTxs`, `CtrlSum`, `PmtInfId`, `PmtMtd`,
  requested execution date, parties/accounts/agents, identifiers, amount,
  remittance, purpose/category purpose, and section 3.
- EPC publication page: downloadable C2PSP XSD ZIP and TVS/production namespace
  disclaimer.
- EPC153-22 v2.1 sections 2–7.5 and the 15 November 2026 address cutover.
- ISO 20022 current catalogue/archive policy and the exact default namespace.
- E1 field map and current `Pain001CanonicalMapper`.

## Claims being approved

- Applicable message and namespace are `pain.001.001.09` and
  `urn:iso:std:iso:20022:tech:xsd:pain.001.001.09`.
- Applicable ISO artifact is the archived 2019
  `pain.001.001.09.xsd`; applicable EPC artifact is the C2PSP TVS ZIP, subject
  to lawful acquisition and checksum pinning.
- ISO XSD, EPC TVS/profile and project business validation are distinct stages.
- Unstructured address references to 22 November 2026 in the unchanged-version
  IG must be read as 15 November 2026 under the EPC banner/guidance.

## Project decisions proposed

Use stable stage-specific safe problems; never silently truncate or flatten
address/remittance/identifier data. Preserve distinct `MsgId`, `PmtInfId`,
`InstrId`, `EndToEndId`, UETR and later `TxId`.

## Open questions

- Exact ISO archive download URL and SHA-256 after authorised acquisition.
- Exact EPC ZIP entry schema(s), checksum and repository distribution terms.
- Group-header versus payment-information control-sum enforcement for E1.
- Date versus date-time requested-execution choice and timezone handling.
- EPC/cardinality rules for optional agents, purpose and address fields in the
  admitted minimal message.

## Rejected alternatives

Latest-message auto-selection; TVS as production namespace; search snippets as
evidence; hand-written substitute TVS; treating the current mapper as scheme
authority.

## Affected use case, slices and stories

`UC-SCT-002`; `UCS-SCT-002-A`, `UCS-SCT-002-B`; `EPIC-19/19.4`,
`EPIC-26/26.3`, `EPIC-26/26.4`, proposed `EPIC-24/24.10`.

## Blocking consequences

Until artifact/profile/field decisions are human-approved, `EPIC-19/19.4`
remains `PARTIALLY_IMPLEMENTED` for Phase E purposes and the cohort is
`VERIFY_PER_USE`, not source-confirmed/ready.

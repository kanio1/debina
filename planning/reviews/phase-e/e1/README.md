# Phase E1 signed pain.001 review pack

Review state: `NOT_REVIEWED`

Authorship: `AI_DRAFT_NOT_HUMAN_REVIEWED`

Prepared: 2026-07-24

Canonical migration: `NOT_ALLOWED`

## Exact review scope

This pack prepares human decisions for `UC-SCT-002`, slices
`UCS-SCT-002-A` and `UCS-SCT-002-B`: authenticated receipt of one signed
`pain.001.001.09`, deterministic provenance/validation outcome, durable
single-payment lineage, and a link to existing payment detail/evidence. It does
not implement the feature, migrate canonical stories, approve compliance, add a
file/batch aggregate, or select a CSM.

The canonical slice contract is explicit: `UCS-SCT-002-A` covers verified
signed input through source-qualified validation, mapping and accepted-payment
lineage. `UCS-SCT-002-B` covers a failed signature, durable safe verdict/evidence
and rejection before parse or payment mapping. Existing payment detail/lineage
is therefore an A-path continuation, not the meaning of B.

The six cohort records are `EPIC-31/31.2`, `EPIC-19/19.2`,
`EPIC-19/19.4`, `EPIC-26/26.3`, `EPIC-26/26.4`, and proposed future
`EPIC-24/24.10`. The proposal is typed as
`PHASE-E1-STORY-PROPOSAL-001`; its placement outcome is
`ADD_STORY_TO_EXISTING_EPIC`.

## Documents and sections inspected

- EPC132-08 / 2025 v1.0: document information, 1.1–1.7, 2.1/2.1.1,
  relevant rows 1.1–2.19 and transaction rows, section 3, address banner,
  public TVS/XSD download notice and disclaimer.
- EPC153-22 v2.1: document information/change history and sections 2–7.5,
  especially address transition sections 6 and 7.2–7.4.
- ISO 20022 message catalogue/current catalogue and archive policy; applicable
  `pain.001.001.09` remains profile-pinned by the EPC 2019 message baseline.
- Debina `UC-SCT-002`, `BR-SCT-003`, accepted ADR-N1…N17, module/context/
  quality catalogues, current mapper/lineage/persistence/signature code and
  existing tests.
- [Field mapping](../../../../docs/data/mappings/E1-PAIN001-SINGLE-INSTRUCTION-MAPPING.yaml),
  [decision register](E1-DECISION-REGISTER.yaml), and
  [approval gate](E1-APPROVALS.yaml).

## Claims submitted for human approval

1. EPC C2PSP establishes the `pain.001.001.09` usage profile but not Debina's
   REST, BFF, detached-header or Ed25519 choices.
2. E1 must distinguish well-formed XML, ISO XSD, EPC TVS/profile, project
   business validation and canonical mapping.
3. Exact raw bytes are archived before signature verification and parsing; this
   is project evidence/security policy.
4. Message, instruction, end-to-end, UETR and later transaction identifiers
   stay semantically distinct.
5. Existing module boundaries are sufficient for the pilot; no new module,
   aggregate, Kafka topic or GraphQL command owner is proposed.

## Project decisions proposed

- Backend REST plus a fixed-destination browser/BFF upload adapter.
- A versioned synthetic `DEBINA-E1-DETACHED-ED25519-V1` profile, subject to
  security review; it is not an EPC requirement.
- ISO XSD + EPC TVS + project-rule stages with lawful artifact acquisition and
  pinned checksums.
- Measured, not invented, size/depth/count/memory/time limits.
- PostgreSQL raw-byte evidence for E1 with approved RLS, access, encryption,
  masking and retention controls.

## Open questions

- Who authoritatively binds `X-Signer-Id` to an E1 verification key?
- May the EPC TVS ZIP and ISO archive XSD be stored in the repository, or only
  fetched/verified under an approved policy?
- Which requested-execution-date choice is admitted, and how is source
  `GrpHdr/CreDtTm` stored distinctly from receive time?
- Which roles may view signature/evidence metadata, and under what retention
  and export policy?
- What measured limits and error compatibility contract are accepted?

## Rejected alternatives

- Treating the latest ISO catalogue version as automatically applicable.
- Treating the EPC TVS namespace as a production namespace.
- Calling Ed25519 or detached HTTP headers an EPC mandate.
- GraphQL mutation/domain ownership, browser token storage, raw XML in logs,
  broad Playwright duplication, or an E1 file/batch/CSM expansion.

## Affected use case, slices and stories

- Use case: `UC-SCT-002`
- Slices: `UCS-SCT-002-A`, `UCS-SCT-002-B`
- Existing stories: `EPIC-31/31.2`, `EPIC-19/19.2`, `EPIC-19/19.4`,
  `EPIC-26/26.3`, `EPIC-26/26.4`
- Future proposal: `EPIC-24/24.10`

## Blocking consequences and architecture review

Until every required queue in `E1-APPROVALS.yaml` is dated
`HUMAN_APPROVED`, all six records remain proposals/current legacy records and
canonical migration is forbidden. The proposed architecture outcome is
`CURRENT_ARCHITECTURE_SUFFICIENT`: REST owns the command, module query ports own
reads, GraphQL stays query-only, BFF owns session adaptation, and frontend owns
no payment semantics. Any new module, aggregate, storage integration or public
port would require its normal admission review.

## Reviewer role, state and approval evidence

Reviewer roles are payments domain, ISO/EPC, security, database/RLS,
architecture, product and QA; legal/source governance is advisory or blocking
where licensing/retention applicability requires it. Current state for every
role is `NOT_REVIEWED`. Dated approval evidence placeholder:
`PENDING_DATED_HUMAN_RECORD`.

## Pack contents

- `E1-DECISION-REGISTER.yaml`
- `E1-APPROVALS.yaml`
- `PAYMENTS-DOMAIN-REVIEW.md`
- `ISO-MESSAGE-REVIEW.md`
- `SECURITY-CHANNEL-SIGNATURE-REVIEW.md`
- `DATABASE-MAPPING-REVIEW.md`
- `PRODUCT-QA-REVIEW.md`

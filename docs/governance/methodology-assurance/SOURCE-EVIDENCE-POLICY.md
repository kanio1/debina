# Source Evidence Policy

Registry presence is source discovery, not proof of a detailed claim. A source
evidence entry records registry ID, title/publisher, document/effective version
when available, section or `SECTION-NOT-AVAILABLE`, message set/version where
applicable, rail, access limitation, supported claim, project interpretation
and confidence. It also records `evidence_status`, verification/review dates,
effective interval, supersession, verification method and reviewer.

`evidence_status` measures structural and source-metadata completeness.
`VERIFIED` means the recorded version, effective date, reviewed section and
claim location are complete enough for the stated claim; it does not mean that
the interpretation has been accepted by a human reviewer.

`semantic_review_state` records domain/ISO/legal human assurance separately.
Allowed values are `NOT_REVIEWED`, `REVIEW_IN_PROGRESS`, `HUMAN_APPROVED`,
`HUMAN_REJECTED` and `CHANGES_REQUESTED`. AI-authored research remains
`NOT_REVIEWED`; a reviewer name or `OFFICIAL_DOCUMENT_REVIEW` method alone is
not approval. Dated approval evidence belongs in the applicable review record.

`VERIFY_PER_USE`,
`INCOMPLETE`, `STALE`, `CONFLICTING` and `RESTRICTED` block
`SOURCE_CONFIRMED + READY`. Unknown dates, an unavailable section or a
use-specific version cannot be called `VERIFIED`.

`SOURCE_CONFIRMED + READY` additionally requires every material evidence entry
to have both `evidence_status: VERIFIED` and
`semantic_review_state: HUMAN_APPROVED`. This gate is deliberately stronger
than source discovery or AI metadata verification.

An accepted/frozen project ADR may support `PROJECT_INTERPRETATION` through the
explicit `PROJECT_ADR` verification method even when external-document
freshness is not applicable; it never proves EPC, ISO or rail truth.
`PROJECT_SIMULATION` additionally requires an explicit synthetic boundary.
Restricted sources store metadata/claim lineage only—never copied participant
content. Use `PARTICIPANT_DOCUMENTATION_REQUIRED` rather than fabricate
non-public rail behavior. Code/tests prove current behavior only.

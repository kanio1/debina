# Source Evidence Policy

Registry presence is source discovery, not proof of a detailed claim. A source
evidence entry records registry ID, title/publisher, document/effective version
when available, section or `SECTION-NOT-AVAILABLE`, message set/version where
applicable, rail, access limitation, supported claim, project interpretation
and confidence. It also records `evidence_status`, verification/review dates,
effective interval, supersession, verification method and reviewer.

`VERIFIED` means the recorded metadata is complete enough for the exact claim;
it does not replace semantic human source review. `VERIFY_PER_USE`,
`INCOMPLETE`, `STALE`, `CONFLICTING` and `RESTRICTED` block
`SOURCE_CONFIRMED + READY`. Unknown dates, an unavailable section or a
use-specific version cannot be called `VERIFIED`.

An accepted/frozen project ADR may support `PROJECT_INTERPRETATION` through the
explicit `PROJECT_ADR` verification method even when external-document
freshness is not applicable; it never proves EPC, ISO or rail truth.
`PROJECT_SIMULATION` additionally requires an explicit synthetic boundary.
Restricted sources store metadata/claim lineage only—never copied participant
content. Use `PARTICIPANT_DOCUMENTATION_REQUIRED` rather than fabricate
non-public rail behavior. Code/tests prove current behavior only.

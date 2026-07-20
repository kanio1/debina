# Source and compliance boundaries

Attach one or more labels to every validation rule, mapping, status interpretation, and claim:

| Label | Use |
| --- | --- |
| `PUBLIC-SOURCE` | Public authoritative material present and applicable. |
| `PROJECT-ADR` | Accepted ADR supplies the project decision. |
| `PROJECT-FREEZE` | Binding `[FREEZE]` supplies the rule. |
| `PARTICIPANT-DOC-REQUIRED` | Participant material is required before implementing or claiming the rule. |
| `CSM-PROFILE-REQUIRED` | Selected CSM profile/TVS is required before implementing or claiming the rule. |
| `CERTIFICATION-EVIDENCE-MISSING` | Do not claim certification readiness. |
| `SOURCE-BLOCKED` | Authoritative detail is absent or conflicting; record the gap. |
| `DO-NOT-CLAIM-COMPLIANCE` | The synthetic platform boundary or absent evidence forbids a compliance claim. |

ADR-N9 makes this a synthetic learning platform, not an EPC-certified, CSM-equivalent, participant-integrated, or legally final settlement system. XSD is structural evidence only. EPC claims require applicable public guidance; CSM claims require the relevant CSM profile/TVS; participant-only requirements must remain blocked until supplied.

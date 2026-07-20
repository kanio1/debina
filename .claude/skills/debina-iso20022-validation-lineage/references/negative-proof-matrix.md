# Negative proof matrix

Select all applicable cases; name the source, expected fail-closed outcome, preserved evidence, and no-effect assertion.

| Case | Required proof |
| --- | --- |
| Schema/namespace/version | Valid and invalid fixtures; wrong namespace/version and unsupported-version failure. |
| Parser hardening | Malformed XML and XXE/external-entity prevention independent of XSD outcome. |
| Signature | Missing/invalid signature and signature-before-parse ordering where required. |
| Identity/correlation | Mismatched identifiers; no conflation; matched/ambiguous/orphaned behavior. |
| Repeat traffic | Duplicate, replay, conflict and business-resubmission distinctions with deterministic effects. |
| Evidence/lineage | Immutable raw evidence, persistent lineage, no source-value regeneration. |
| Isolation | Cross-tenant isolation for evidence, identifiers, and correlation facts. |
| Change resistance | Mutation proof for a critical guard and source-traceability assertion for every applied rule. |

Never use a valid XSD fixture as proof of secure parsing, signature validity, EPC/CSM compliance, authorization, correlation correctness, or finality.

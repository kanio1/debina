# ATAM-inspired Desk Review

**Classification:** `ATAM_INSPIRED_DESK_REVIEW`. This is an agent/team desk analysis, not a stakeholder tradeoff workshop or Full ATAM. It identifies business drivers, quality scenarios, approaches, possible sensitivity/tradeoff points, risk/non-risk hypotheses and experiments; it does not claim stakeholder consensus or utility-tree prioritization.

Business drivers are fidelity without false compliance, safe money semantics, tenant security, recoverability and affordable local verification. This review analyzes existing/frozen approaches; it does not change ADRs.

| Approach | Sensitivity / trade-off | Assessment |
|---|---|---|
| Spring modular monolith | module API clarity versus physical package convenience | retain; ISO/ingress packaging is a boundary finding |
| one-writer schemas + RLS | safety versus developer ergonomics | retain; grants/RLS tests are required |
| same-transaction cross-module ports | atomicity versus ownership/connection complexity | retain only approved gross-instant path; review new use cases |
| per-schema outbox/inbox + Kafka | recoverability versus ordering/operations | retain; prove failure windows per owner |
| GraphQL Query-only + REST commands | read flexibility versus transport god-module risk | retain demand-driven policy; add source-port fitness checks |
| BFF token model | browser safety versus contract duplication | retain; avoid business decisions in BFF |
| rail-neutral core + rail adapters | reuse versus false commonality | retain only intent; keep submission/batch/cycle rail-specific |
| separate evidence/audit | provenance versus lookup complexity | retain; evaluate investigation index by use case |
| local Dagger, late full Playwright | consistent local feedback versus no remote coverage/system UI risk | retain; remote CI deferred, minimal smoke is later validation only |

Risks: rail leakage, same-transaction complexity, transport growth and planning drift. Non-risks: lack of a remote provider is an intentional deferral, not an unrecorded gap. Recommended experiments: maker-checker UC2 pilot; source-qualified file/group model; one DF-08 correlation slice; local verification timing baseline. Potential `ADR-REVIEW-CANDIDATE`: whether an explicit ISO/ingress module extraction is justified after UC2 evidence; no frozen decision is superseded.

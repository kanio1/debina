---
id: UC-SCTINST-001
status: PROVISIONAL
enforcement: ENFORCED
methodology: {narrative: cockburn_fully_dressed, decomposition: use_case_2_0, rule_elaboration: example_mapping, payment_process_model: iso_20022_business_process_catalogue, domain_modeling: domain_driven_design, architecture_views: c4, quality_requirements: arc42_quality_scenarios, architecture_evaluation: atam_lite}
---
# Submit an SCT Inst instruction
**Business process:** BP-01; system level; **primary actor:** payment submitter; supporting: routing/settlement. Interest: an eligible single transaction reaches an explicit outcome. Trigger: instant instruction supplied.

**Methodology assurance correction:** Debina is the system of interest; payment submitter is the external human actor. Routing and settlement are architecture realization. Profile is ESSENTIAL; discovery is `AI_DRAFT`/`NOT_REVIEWED` and rail-timeout remains material.
## Preconditions and guarantees
Applicable SCT Inst profile and route selection exist. Minimal: no financial effect on invalid/failed submission. Success: instruction is accepted for route/settlement or rejected with correlation.
## Main success scenario
BF-1. The submitter provides one instant instruction.
BF-2. Debina identifies tenant and channel.
BF-3. Debina validates the qualified source/profile.
BF-4. Debina records lineage and applies approval policy.
BF-5. Debina selects an eligible route.
BF-6. Debina reports the accepted processing outcome.
## Extensions and failure flows
AF-3A. At BF-3, unsupported profile/version rejects with correlation; terminate.
AF-4A. At BF-4, approval retains the instruction; rejoin outcome at BF-6.
CF-5A. At BF-5, no eligible route fails closed; terminate. Rail timing/deadline is explicitly source-blocked.
## Rules, sources and rail applicability
BR-SCTINST-001. `[EPC-SCT-INST]`, `[ISO20022]`; generic SCT Inst APPLICABLE; RT1/TIPS/STET APPLICABLE-WITH-RAIL-EXTENSION, named timing/submission rules SOURCE-BLOCKED; SCT/STEP2 NOT-APPLICABLE.
## Special requirements and variations
QS-INT-01/QS-SEC-01/QS-REL-01. Single transaction intent is not a TIPS/RT1 contract claim. Commands submit/route; sources payment/iso; no authoritative volume target.
## Architecture, ATAM and slices
payment/routing/settlement participation; `CURRENT-ARCHITECTURE-SUFFICIENT` for project strategy, `QUALITY-EXPERIMENT-REQUIRED` for rail deadline. **A** accept eligible instant input, CAPABILITY-BLOCKED. **B** fail closed no route, READY-CANDIDATE. Examples valid, invalid amount/currency, duplicate key; question rail timeout; out batch/file.
## Traceability and open scope
EPIC-19/33/35; runtime proof requires route plus finality evidence. Open rail profile semantics.

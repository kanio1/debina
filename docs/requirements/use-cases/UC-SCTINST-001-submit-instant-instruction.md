---
id: UC-SCTINST-001
status: PROVISIONAL
enforcement: ENFORCED
methodology: {narrative: cockburn_fully_dressed, decomposition: use_case_2_0, rule_elaboration: example_mapping, payment_process_model: iso_20022_business_process_catalogue, domain_modeling: domain_driven_design, architecture_views: c4, quality_requirements: arc42_quality_scenarios, architecture_evaluation: atam_lite}
---
# Submit an SCT Inst instruction
**Business process:** BP-01; system level; **primary actor:** payment submitter; supporting: routing/settlement. Interest: an eligible single transaction reaches an explicit outcome. Trigger: instant instruction supplied.
## Preconditions and guarantees
Applicable SCT Inst profile and route selection exist. Minimal: no financial effect on invalid/failed submission. Success: instruction is accepted for route/settlement or rejected with correlation.
## Main success scenario
1. Submitter provides one instant instruction. 2. Debina identifies tenant/channel. 3. Debina validates source/profile. 4. Debina records lineage and applies approval policy. 5. Debina selects eligible route. 6. Debina reports accepted processing outcome.
## Extensions and failure flows
3a unsupported profile/version rejects. 4a approval gate retains it. 5a no eligible route fails closed. Settlement timing/deadline is rail-specific and not asserted here.
## Rules, sources and rail applicability
BR-SCTINST-001. `[EPC-SCT-INST]`, `[ISO20022]`; generic SCT Inst APPLICABLE; RT1/TIPS/STET APPLICABLE-WITH-RAIL-EXTENSION, named timing/submission rules SOURCE-BLOCKED; SCT/STEP2 NOT-APPLICABLE.
## Special requirements and variations
QS-INT-01/QS-SEC-01/QS-REL-01. Single transaction intent is not a TIPS/RT1 contract claim. Commands submit/route; sources payment/iso; no authoritative volume target.
## Architecture, ATAM and slices
payment/routing/settlement participation; `CURRENT-ARCHITECTURE-SUFFICIENT` for project strategy, `QUALITY-EXPERIMENT-REQUIRED` for rail deadline. **A** accept eligible instant input, CAPABILITY-BLOCKED. **B** fail closed no route, READY-CANDIDATE. Examples valid, invalid amount/currency, duplicate key; question rail timeout; out batch/file.
## Traceability and open scope
EPIC-19/33/35; runtime proof requires route plus finality evidence. Open rail profile semantics.

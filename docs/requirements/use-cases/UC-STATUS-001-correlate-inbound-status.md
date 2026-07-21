---
id: UC-STATUS-001
status: APPROVED
enforcement: ENFORCED
methodology: {narrative: cockburn_fully_dressed, decomposition: use_case_2_0, rule_elaboration: example_mapping, payment_process_model: iso_20022_business_process_catalogue, domain_modeling: domain_driven_design, architecture_views: c4, quality_requirements: arc42_quality_scenarios, architecture_evaluation: atam_lite}
---
# Correlate an inbound payment status
**BP-07; system; primary:** external CSM; supporting operations analyst, ISO-adapter. Trigger: recorded pacs.002. Preconditions: source message evidence. Minimal: unknown/ambiguous status does not mutate lifecycle. Success: correlation classification and evidence are durable.
## Main success scenario
1. External system provides status report. 2. Debina preserves/validates message evidence. 3. Debina resolves original identifiers. 4. Debina classifies matched/orphan/ambiguous result. 5. Debina publishes only source-owned classification outcome. 6. Debina makes evidence available.
## Extensions/failures
3a unknown: orphan; 3b conflicting identifiers: ambiguous; 2a duplicate/out-of-order: preserve/classify, do not collapse status axes. Lifecycle handoff is separate capability.
## Rules/sources/rails
BR-STATUS-001/002; `[ISO20022]`, `[EPC-SCT]`, `[EPC-SCT-INST]`; SCT/SCT Inst APPLICABLE, STEP2/RT1/TIPS/STET APPLICABLE-WITH-RAIL-EXTENSION. ISO hierarchy: status report/pacs.002 is a message, correlation is actor goal.
## Quality/data/architecture
QS-TRC-01/QS-REL-01/QS-MOD-01; ISO owns messages/correlation/outbox, payment owns lifecycle. `CURRENT-ARCHITECTURE-SUFFICIENT`; ATAM sensitivity identifier quality/order. Slices A matched, READY-CANDIDATE; B orphan/ambiguous, READY-CANDIDATE. Examples known/unknown/duplicate/conflict; out status-to-finality inference. EPIC-26–28; coverage correlation-only.

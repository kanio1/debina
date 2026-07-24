---
id: UC-RTRANS-001
status: SOURCE_BLOCKED
enforcement: ENFORCED
methodology: {narrative: cockburn_fully_dressed, decomposition: use_case_2_0, rule_elaboration: example_mapping, payment_process_model: iso_20022_business_process_catalogue, domain_modeling: domain_driven_design, architecture_views: c4, quality_requirements: arc42_quality_scenarios, architecture_evaluation: atam_lite}
---
# Handle a reject or return
**BP-08; system; primary:** case investigator; supporting external CSM. Trigger: scheme-qualified exception evidence. Preconditions: original context and applicable profile. Minimal: no ledger reversal or fabricated eligibility. Success: reject/return is classified and correlated.
## Main success scenario
1. Investigator receives exception. 2. Debina identifies original context. 3. Debina classifies reject versus return. 4. Debina records source-qualified decision/evidence. 5. Debina coordinates permitted response.
## Extensions/failures
2a original unknown: case evidence only. 3a eligibility/rule absent: SOURCE_BLOCKED. 4a return after finality is a new opposite-direction payment, never reversal.
## Rules/sources/rails
BR-RTRANS-001/002; EPC SCT/SCT Inst/ISO sources; all named rail extensions SOURCE_BLOCKED. Reject and return are distinct business documents/outcomes.
## Quality/data/architecture
QS-TRC-01/QS-INT-02; future case context, ISO/egress adapters; `NEW-ADR-REQUIRED` only if a money effect is proposed. Slices A reject, B return, both SOURCE_BLOCKED. Examples reject/pre-finality return/post-finality return; out automatic repair. EPIC-65–66; no implementation.

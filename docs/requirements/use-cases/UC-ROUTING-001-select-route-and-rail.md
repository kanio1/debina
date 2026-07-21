---
id: UC-ROUTING-001
status: PROVISIONAL
enforcement: ENFORCED
methodology: {narrative: cockburn_fully_dressed, decomposition: use_case_2_0, rule_elaboration: example_mapping, payment_process_model: iso_20022_business_process_catalogue, domain_modeling: domain_driven_design, architecture_views: c4, quality_requirements: arc42_quality_scenarios, architecture_evaluation: atam_lite}
---
# Select a route and rail
**BP-04; system scope/level; primary:** system-service-identity; supporting reference-data administrator. Stakeholders require explainable, fail-closed route evidence. Trigger: eligible payment reaches routing.
## Preconditions and guarantees
Validated payment and configured candidate data exist. Minimal: no route is selected without an eligible explanation. Success: one evidence-backed route decision is available.
## Main success scenario
1. Debina receives a route request. 2. It reads applicable scheme/profile candidates. 3. It evaluates reachability, cutoff, settlement basis and liquidity mode. 4. It records outcome/reason. 5. It returns selected route or fail-closed result.
## Extensions and failures
3a no candidate eligible: fail closed with reason. 3b fallback applies only by configured rule identity. 2a participant policy absent: SOURCE-BLOCKED.
## Rules/sources/rails
BR-ROUTING-001/002; `[EPC-SCT]`, `[EPC-SCT-INST]`, `[PROJECT-ADR] project-adr-n10`. SCT/SCT Inst APPLICABLE-WITH-RAIL-EXTENSION; STEP2/RT1/TIPS/STET PARTICIPANT-DOCUMENTATION-REQUIRED. ISO hierarchy: payment transaction → route decision; no message definition is the goal.
## Quality/data/architecture
QS-MOD-01/QS-SEC-01; routing schema owns decision, reference-data owns profile, no cross-owner writes. Command FallbackDecisionCommand; query candidates; route evidence event. `CURRENT-ARCHITECTURE-SUFFICIENT`; ATAM sensitivity is strategy identity, risk participant policy. Slices A select eligible route (CAPABILITY-BLOCKED), B fail closed (READY-CANDIDATE). Example Mapping: eligible/no eligible/fallback; question participant reachability; out rail invention. EPIC-51–55; coverage partial; no volume target.

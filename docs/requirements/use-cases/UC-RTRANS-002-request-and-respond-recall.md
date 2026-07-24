---
id: UC-RTRANS-002
status: SOURCE_BLOCKED
enforcement: ENFORCED
methodology: {narrative: cockburn_fully_dressed, decomposition: use_case_2_0, rule_elaboration: example_mapping, payment_process_model: iso_20022_business_process_catalogue, domain_modeling: domain_driven_design, architecture_views: c4, quality_requirements: arc42_quality_scenarios, architecture_evaluation: atam_lite}
---
# Request and respond to a recall
**BP-08; system; primary:** case investigator; supporting external CSM. Trigger: recall request or response. Preconditions: correlated original/case/profile. Minimal: no automatic money mutation. Success: case decision and response evidence are linked.
## Main success scenario
1. Investigator submits/receives recall. 2. Debina correlates original/case. 3. Debina records request or response. 4. Investigator makes permitted decision. 5. Debina prepares source-qualified response evidence.
## Extensions/failures
2a no context: record unresolved case. 4a deadline/eligibility unknown: SOURCE_BLOCKED. 5a transport failure remains transport state only.
## Rules/sources/rails
BR-RTRANS-003; EPC/ISO sources; rail applicability SOURCE_BLOCKED/PARTICIPANT_DOCUMENTATION_REQUIRED. camt.056/camt.029 are messages, not goals.
## Quality/data/architecture
QS-TRC-01/QS-REL-02; future case, egress and ISO; `BOUNDARY-REVIEW-CANDIDATE`. A request, B response, both SOURCE_BLOCKED. Examples known/unknown original, response; questions source profile/time; out auto-reversal. EPIC-65–70.

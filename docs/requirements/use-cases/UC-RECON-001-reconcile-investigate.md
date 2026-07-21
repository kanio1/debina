---
id: UC-RECON-001
status: PROVISIONAL
enforcement: ENFORCED
methodology: {narrative: cockburn_fully_dressed, decomposition: use_case_2_0, rule_elaboration: example_mapping, payment_process_model: iso_20022_business_process_catalogue, domain_modeling: domain_driven_design, architecture_views: c4, quality_requirements: arc42_quality_scenarios, architecture_evaluation: atam_lite}
---
# Reconcile and investigate an exception
**BP-10; system; primary:** reconciliation analyst; supporting case investigator. Trigger: evidence comparison identifies a difference. Preconditions: source evidence is available. Minimal: no automatic repair. Success: finding is explained/escalated.
## Main success scenario
1. Analyst selects reconciliation scope. 2. Debina compares source-owned evidence. 3. Debina presents discrepancy. 4. Analyst records finding/escalates. 5. Debina preserves read-only evidence.
## Extensions/failures
2a evidence missing: report incompleteness. 3a unauthorized scope: no disclosure. 4a case unavailable: retain finding, do not repair.
## Rules/sources/rails
BR-RECON-001; `[PROJECT-ADR]`; all rails project interpretation until source profile. No camt rule asserted.
## Quality/data/architecture
QS-TRC-01/QS-SEC-01; future reconciliation, evidence-audit query ownership, case future. `BOUNDARY-REVIEW-CANDIDATE`; ATAM read-only/non-repair sensitivity. A detect, B escalate, CAPABILITY-BLOCKED. Examples missing/inconsistent evidence; out repair. EPIC-57–64.

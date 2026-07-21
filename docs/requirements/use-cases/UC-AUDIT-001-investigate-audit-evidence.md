---
id: UC-AUDIT-001
status: APPROVED
enforcement: ENFORCED
methodology: {narrative: cockburn_fully_dressed, decomposition: use_case_2_0, rule_elaboration: example_mapping, payment_process_model: iso_20022_business_process_catalogue, domain_modeling: domain_driven_design, architecture_views: c4, quality_requirements: arc42_quality_scenarios, architecture_evaluation: atam_lite}
---
# Investigate audit and evidence
**BP-10; system; primary:** auditor; supporting operations analyst. Trigger: auditor supplies payment/message/audit identifier. Preconditions: authorized scope. Minimal: no data beyond authorized source-owned views. Success: linked audit/evidence is located with correlation.
## Main success scenario
1. Auditor supplies identifier/filter. 2. Debina authorizes scope. 3. Debina queries source-owned payment/ISO/audit reads. 4. Debina correlates available evidence. 5. Debina presents results and gaps.
## Extensions/failures
2a unauthorized: deny/no foreign existence. 3a fragment missing: report gap. 4a correlation inconsistent: preserve evidence, do not repair.
## Rules/sources/rails
BR-AUDIT-001; project ADR and ISO identifier source. All rails APPLICABLE as operational read, not as rail behavior.
## Quality/data/architecture
QS-SEC-01/QS-TRC-01; evidence-audit/payment/ISO query ports, Query-only GraphQL, BFF adapter; `CURRENT-ARCHITECTURE-SUFFICIENT`. A find payment lineage, READY-CANDIDATE; B audit trail, READY-CANDIDATE. Examples business/ISO ID, tenant restriction, missing fragment; out export. EPIC-77/78; partial coverage.

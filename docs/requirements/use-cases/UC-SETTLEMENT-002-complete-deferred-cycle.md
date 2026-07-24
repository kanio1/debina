---
id: UC-SETTLEMENT-002
status: SOURCE_BLOCKED
enforcement: ENFORCED
methodology: {narrative: cockburn_fully_dressed, decomposition: use_case_2_0, rule_elaboration: example_mapping, payment_process_model: iso_20022_business_process_catalogue, domain_modeling: domain_driven_design, architecture_views: c4, quality_requirements: arc42_quality_scenarios, architecture_evaluation: atam_lite}
---
# Complete deferred-cycle settlement
**BP-06; system; primary:** settlement operator. Trigger: project deferred strategy selects a cycle. Preconditions: cycle/position evidence. Minimal: no universal batch/finality behavior. Success: project cycle outcome recorded.
## Main success scenario
1. Operator selects cycle. 2. Debina assigns eligible items/positions. 3. Debina evaluates configured cycle completion. 4. Debina records outcome. 5. Debina returns cycle evidence.
## Extensions/failures
2a inconsistent membership rejects. 3a source-specific trigger unavailable: SOURCE_BLOCKED. Concurrent closing uses project command receipt; no rail claim.
## Rules/sources/rails
BR-SETTLEMENT-003; `[STEP2]`, `[STET]`, project ADR. STEP2/STET SOURCE_BLOCKED; SCT APPLICABLE-WITH-RAIL-EXTENSION; instant rails NOT-APPLICABLE.
## Quality/data/architecture
QS-INT-02/QS-MOD-01; settlement owns cycles, ledger effect requires LedgerPort. `AGGREGATE-REVIEW-CANDIDATE`; ATAM cycle consistency trade-off. Slice A close project cycle, SOURCE_BLOCKED. Examples positions/race; question rail finality; out universal batch. EPIC-34/37–38; project coverage only.

---
id: UC-SETTLEMENT-001
status: PROVISIONAL
enforcement: ENFORCED
methodology: {narrative: cockburn_fully_dressed, decomposition: use_case_2_0, rule_elaboration: example_mapping, payment_process_model: iso_20022_business_process_catalogue, domain_modeling: domain_driven_design, architecture_views: c4, quality_requirements: arc42_quality_scenarios, architecture_evaluation: atam_lite}
---
# Settle an instant payment
**BP-05; system; primary:** settlement operator; supporting liquidity operator, ledger. Trigger: selected gross-instant project strategy. Preconditions: payment/strategy/finality rule exist. Minimal: no partial money effect. Success: one attempt has reservation/post/release and explicit finality record where applicable.
## Main success scenario
1. Operator initiates attempt. 2. Debina reads strategy/rule. 3. Debina reserves liquidity through LedgerPort. 4. Debina posts or releases in the approved transaction boundary. 5. Debina records outcome/finality evidence. 6. Debina returns attempt outcome.
## Extensions/failures
3a insufficient liquidity: release/no post. 4a failure: rollback partial effect. 5a duplicate/concurrent attempt: idempotent conflict handling. Rail timing is not inferred.
## Rules/sources/rails
BR-SETTLEMENT-001/002; `[PROJECT-ADR] project-adr-n10`, `[EPC-SCT-INST]`; SCT Inst APPLICABLE-WITH-RAIL-EXTENSION; TIPS/RT1/STET SOURCE_BLOCKED; SCT/STEP2 NOT-APPLICABLE.
## Quality/data/architecture
QS-INT-02/QS-REL-01; settlement/ledger/payment owned schemas, LedgerPort-only money path, same transaction coordinator. `CURRENT_ARCHITECTURE_SUFFICIENT`; ATAM sensitivity transaction/role boundary, risk rail trigger evidence. A sufficient liquidity, CAPABILITY-BLOCKED; B insufficient liquidity, READY-CANDIDATE. Examples success/duplicate/race; out return reversal. EPIC-33/36; coverage project strategy.

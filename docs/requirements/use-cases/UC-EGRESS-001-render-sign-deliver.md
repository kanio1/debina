---
id: UC-EGRESS-001
status: PROVISIONAL
enforcement: ENFORCED
methodology: {narrative: cockburn_fully_dressed, decomposition: use_case_2_0, rule_elaboration: example_mapping, payment_process_model: iso_20022_business_process_catalogue, domain_modeling: domain_driven_design, architecture_views: c4, quality_requirements: arc42_quality_scenarios, architecture_evaluation: atam_lite}
---
# Render sign and deliver an outbound message
**BP-09; system; primary:** service identity; supporting operator, signature. Trigger: source-owned obligation. Preconditions: render profile and routing decision. Minimal: transport state does not assert payment finality. Success: attempt/evidence is recorded.
## Main success scenario
1. Debina receives obligation. 2. It selects render profile. 3. It renders/signs where applicable. 4. It dispatches through owned transport. 5. It records attempt/receipt evidence. 6. It exposes transport outcome separately.
## Extensions/failures
2a profile absent: fail closed. 3a signing fails: no dispatch. 4a timeout: retry per profile; no finality update. 5a receipt semantics are rail-specific.
## Rules/sources/rails
BR-EGRESS-001; ISO/project ADR; all clearing rails APPLICABLE-WITH-RAIL-EXTENSION or SOURCE_BLOCKED. TransportFile is not a clearing submission.
## Quality/data/architecture
QS-REL-02/QS-TRC-01; egress owns transport schema, signature key material; outbox boundary. `CURRENT_ARCHITECTURE_SUFFICIENT` for transport-only, render/receipt `SOURCE_BLOCKED`. A render/sign, CAPABILITY-BLOCKED; B delivery receipt, SOURCE_BLOCKED. Examples sign failure/timeout; out finality. EPIC-43–50.

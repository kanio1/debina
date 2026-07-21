---
id: UC-SCT-002
status: PROVISIONAL
enforcement: ENFORCED
methodology: {narrative: cockburn_fully_dressed, decomposition: use_case_2_0, rule_elaboration: example_mapping, payment_process_model: iso_20022_business_process_catalogue, domain_modeling: domain_driven_design, architecture_views: c4, quality_requirements: arc42_quality_scenarios, architecture_evaluation: atam_lite}
---
# Submit a signed pain.001 instruction
**Business process:** BP-01; **scope/level:** system; **primary actor:** payment submitter; **supporting:** signature, ingress, ISO-adapter. **Interests:** source evidence precedes mapping. **Trigger:** a signed pain.001 arrives.
## Preconditions and guarantees
Tenant/profile and verification material are available. Minimal: raw source and verdict evidence survive failure where permitted. Success: verified source is mapped as one instruction and proceeds through UC-SCT-001.
## Main success scenario
1. Submitter supplies signed pain.001. 2. Debina identifies channel/tenant. 3. Debina preserves source evidence and verifies signature. 4. Debina validates/maps the applicable message. 5. Debina delegates accepted instruction handling to UC-SCT-001. 6. Debina returns correlation and outcome.
## Extensions and failure flows
3a. Signature fails: record verdict/evidence and reject; no mapping. 4a. Version/structure is unsupported: reject with preserved source correlation. 5a. Replay follows the idempotency model.
## Rules, sources and rail applicability
BR-SCT-003. `[ISO20022] iso20022-catalogue`, `[EPC-SCT] epc-sct`; generic SCT APPLICABLE, SCT Inst APPLICABLE-WITH-RAIL-EXTENSION, named rail profiles PARTICIPANT-DOCUMENTATION-REQUIRED. ISO hierarchy: initiation/message/business envelope/pain.001; signature policy is project interpretation, not ISO scheme timing.
## Special requirements and variations
QS-SEC-02, QS-TRC-01, QS-INT-01. Privacy: raw evidence access is source-owned. Data/commands: signature verification, ingress evidence, mapping; transaction boundary source-evidence-and-payment. Variations: unsigned JSON_DIRECT is out. No volume target.
## Architecture, ATAM and slices
signature technical adapter, ingress/ISO/payment-lifecycle; `CURRENT-ARCHITECTURE-SUFFICIENT`, but profile rules are `SOURCE-BLOCKED`. ATAM sensitivity: verify-before-map; risk is false provenance. **A** verified signed input, CAPABILITY-BLOCKED; **B** failed signature, READY-CANDIDATE. Example Mapping: rule signature precedes mapping; examples valid/failed signature; question channel certificate profile; out: participant signature policy.
## Traceability and open scope
EPIC-26, EPIC-31 and Wave 12 checkpoint are evidence only (not completion proof). Test strategy signature/lineage proof. Open: exact scheme/profile applicability; out: Wave 12 implementation.

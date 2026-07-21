---
id: UC-SCT-001
status: APPROVED
enforcement: ENFORCED
methodology: {narrative: cockburn_fully_dressed, decomposition: use_case_2_0, rule_elaboration: example_mapping, payment_process_model: iso_20022_business_process_catalogue, domain_modeling: domain_driven_design, architecture_views: c4, quality_requirements: arc42_quality_scenarios, architecture_evaluation: atam_lite}
---
# Submit a single SCT instruction
**Business process:** BP-01. **Scope/level:** Debina/system. **Primary actor:** payment submitter; **supporting:** ingress, ISO-adapter, payment-lifecycle. **Stakeholders:** submitter needs a durable outcome; operations needs correlation evidence. **Trigger:** submitter presents one instruction.
## Preconditions and guarantees
Authenticated tenant/channel and supported profile are available. Minimal guarantee: rejected/replayed input preserves correlation/evidence without a duplicate payment. Success guarantee: one accepted instruction has lineage and is either approval-gated or released.
## Main success scenario
1. The submitter provides one SCT instruction. 2. Debina identifies tenant, channel and submitter. 3. Debina validates applicable scheme/channel input. 4. Debina preserves source lineage and creates the payment representation. 5. Debina applies the approval gate. 6. Debina returns outcome and correlation identifiers.
## Extensions and failure flows
3a. Mandatory data/profile is unsupported: Debina rejects with reason and correlation. 4a. Same idempotency key/equivalent payload: return original outcome; different payload conflicts. 5a. Approval is required: create pending approval and do not release processing.
## Rules, sources and rail applicability
Rules: BR-SCT-001/002. Sources `[EPC-SCT] epc-sct`, `[ISO20022] iso20022-catalogue`; generic SCT APPLICABLE, STEP2 APPLICABLE-WITH-RAIL-EXTENSION, SCT Inst NOT-APPLICABLE, RT1/TIPS/STET NOT-APPLICABLE. ISO hierarchy: payment initiation → BP-01 → credit-transfer transaction → pain.001/JSON_DIRECT project channel.
## Special requirements and variations
QS-INT-01, QS-SEC-01, QS-TRC-01, QS-REL-01; privacy: tenant/branch-minimized identifiers. Variations: JSON_DIRECT is `[PROJECT-SIMULATION]`; pain.001 is addressed by UC-SCT-002. Frequency/volume: no authoritative target. Data: payment/ingress/iso schemas; commands submit; query evidence; event payment.submitted; idempotency key; payment/evidence transaction.
## Architecture, ATAM and slices
payment-lifecycle owns outcome, ingress/ISO own evidence; REST command and Query-only GraphQL/BFF reads are adapters; `CURRENT-ARCHITECTURE-SUFFICIENT`. ATAM: idempotency/evidence boundary is sensitivity point; experiment preserves changed-payload conflict proof. **A** accept valid instruction: start supplied/end accepted or gated, READY-CANDIDATE. **B** reject/replay: start supplied/end rejection/original outcome, READY-CANDIDATE. Example Mapping: Rules BR-SCT-001/002; examples valid, missing field, same/different key; question profile scope; out: files/groups.
## Traceability and open scope
EPIC-19, EPIC-76; test strategy submission/idempotency runtime proofs; current coverage partial. Open: participant channel profile. Out: file/group semantics.

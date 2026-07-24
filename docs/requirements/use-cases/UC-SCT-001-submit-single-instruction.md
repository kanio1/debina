---
id: UC-SCT-001
status: APPROVED
enforcement: ENFORCED
methodology_assurance: ENFORCED
system_of_interest: Debina
system_boundary: Debina payment-processing system
primary_actor_type: human_role
actor_goal: submit one SCT instruction and receive a durable outcome
goal_level: user_goal
detail_profile: ESSENTIAL
discovery: {origin: AI_DRAFT, collaboration_status: NOT_REVIEWED, material_questions_open: true, review_evidence: []}
collaboration_status: NOT_REVIEWED
source_evidence_status: PROJECT_AND_REGISTRY_CLASSIFIED
architecture_evaluation_type: ATAM_INSPIRED_DESK_REVIEW
methodology: {narrative: cockburn_fully_dressed, decomposition: use_case_2_0, rule_elaboration: example_mapping, payment_process_model: iso_20022_business_process_catalogue, domain_modeling: domain_driven_design, architecture_views: c4, quality_requirements: arc42_quality_scenarios, architecture_evaluation: atam_lite}
---
# Submit a single SCT instruction
**Business process:** BP-01. **Scope/level:** Debina/system user goal. **Primary actor:** payment submitter. Internal ingress, ISO-adapter and payment-lifecycle are architecture realization. **Stakeholders:** submitter needs a durable outcome; operations needs correlation evidence. **Trigger:** submitter presents one instruction.
## Preconditions and guarantees
Authenticated tenant/channel and supported profile are available. Minimal guarantee: rejected/replayed input preserves correlation/evidence without a duplicate payment. Success guarantee: one accepted instruction has lineage and is either approval-gated or released.
## Main success scenario
BF-1. The submitter provides one SCT instruction.
BF-2. Debina identifies the tenant, channel and authenticated submitter.
BF-3. Debina validates the applicable scheme/channel input.
BF-4. Debina preserves source lineage and creates the payment representation.
BF-5. Debina applies the approval gate.
BF-6. Debina returns the accepted, rejected or pending outcome and correlation identifiers.
## Extensions and failure flows
AF-3A — unsupported input. At BF-3, when mandatory data/profile is unsupported: Debina rejects with reason/correlation and terminates with minimal guarantee.
AF-4A — replay. At BF-4, equivalent idempotency key returns original outcome; different payload conflicts; rejoin BF-6 or terminate.
AF-5A — approval required. At BF-5, Debina creates pending approval and does not release processing; rejoin BF-6.
## Rules, sources and rail applicability
Rules: BR-SCT-001/002. Sources `[EPC-SCT] epc-sct`, `[ISO20022] iso20022-catalogue`; generic SCT APPLICABLE, STEP2 APPLICABLE-WITH-RAIL-EXTENSION, SCT Inst NOT-APPLICABLE, RT1/TIPS/STET NOT-APPLICABLE. ISO hierarchy: payment initiation → BP-01 → credit-transfer transaction → pain.001/JSON_DIRECT project channel.
## Special requirements and variations
QS-INT-01, QS-SEC-01, QS-TRC-01, QS-REL-01; privacy: tenant/branch-minimized identifiers. Variations: JSON_DIRECT is `[PROJECT_SIMULATION]`; pain.001 is addressed by UC-SCT-002. Frequency/volume: no authoritative target. Data: payment/ingress/iso schemas; commands submit; query evidence; event payment.submitted; idempotency key; payment/evidence transaction.
## Architecture, ATAM and slices
payment-lifecycle owns outcome, ingress/ISO own evidence; REST command and Query-only GraphQL/BFF reads are adapters; `CURRENT_ARCHITECTURE_SUFFICIENT`. ATAM-inspired desk review: idempotency/evidence boundary is a possible sensitivity; no stakeholder consensus claimed. **A** accept valid instruction selects BF-1..BF-6: start supplied/end accepted or gated, READY-CANDIDATE. **B** reject/replay selects AF-3A/AF-4A: start supplied/end rejection/original outcome, READY-CANDIDATE. Example Mapping (`AI_DRAFT`): Rules BR-SCT-001/002; examples valid, missing field, same/different key; material question profile scope (owner: channel policy, blocks rail-specific READY); **Debina extension—out of scope:** files/groups.
## Traceability and open scope
EPIC-19, EPIC-76; test strategy submission/idempotency runtime proofs; current coverage partial. Open: participant channel profile. Out: file/group semantics.

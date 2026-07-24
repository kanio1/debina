---
id: UC-SCT-003
status: SOURCE_BLOCKED
enforcement: ENFORCED
methodology: {narrative: cockburn_fully_dressed, decomposition: use_case_2_0, rule_elaboration: example_mapping, payment_process_model: iso_20022_business_process_catalogue, domain_modeling: domain_driven_design, architecture_views: c4, quality_requirements: arc42_quality_scenarios, architecture_evaluation: atam_lite}
---
# Submit a file-based SCT instruction
**Business process:** BP-02; **scope/level:** system; **primary actor:** payment submitter; **supporting:** ingress/ISO-adapter. Stakeholders need preserved file/group/item evidence. Trigger: submitter provides a file.

**Methodology assurance correction:** system of interest is Debina; primary actor is the external human payment submitter. Ingress/ISO-adapter are architecture realization. Profile is OUTLINE because material source questions remain; discovery is `AI_DRAFT`/`NOT_REVIEWED`.
## Preconditions and guarantees
Supported file/profile is selected. Minimal: receipt/evidence is correlated and no invented partial outcome occurs. Success: source-qualified file/group/transaction outcome is recorded.
## Main success scenario
BF-1. The submitter provides a file.
BF-2. Debina identifies tenant and channel.
BF-3. Debina records file evidence and identifies message/profile.
BF-4. Debina validates header/group/transaction structure.
BF-5. Debina records only source-qualified outcomes.
BF-6. Debina returns file correlation.
## Extensions and failure flows
AF-3A. At BF-3, unsupported version rejects while preserving evidence; terminate with minimal guarantee.
CF-4A. At BF-4, header count/control-sum mismatch rejects at the supported level; terminate.
CF-4B. At BF-4, one invalid transaction remains SOURCE_BLOCKED; do not infer partial processing.
## Rules, sources and rail applicability
BR-SCT-004. `[ISO20022]`, `[EPC-SCT]`, `[STEP2]`; generic SCT APPLICABLE-WITH-RAIL-EXTENSION, STEP2 SOURCE_BLOCKED pending profile, SCT Inst/RT1/TIPS NOT-APPLICABLE, STET PARTICIPANT_DOCUMENTATION_REQUIRED. ISO hierarchy: initiation/file/business message/group/transaction/pain.001.
## Special requirements and variations
QS-TRC-01/QS-INT-01/QS-MOD-01. File, group, and transaction are distinct; no volume target. Data/commands/file ingestion are PLANNED; no transaction boundary chosen.
## Architecture, ATAM and slices
Future file-ingestion boundary needs `AGGREGATE-REVIEW-CANDIDATE`; current architecture is insufficient for outcomes. **A** receive/validate one file, SOURCE_BLOCKED. **B** record group/item outcomes, SOURCE_BLOCKED. Example Mapping: one group/one item; several groups; count/sum mismatch; question partial processing; out: universal batch semantics.
## Traceability and open scope
EPIC-73; test strategy source-profile fixtures. Participant documentation is required for rail submission/outcome behavior.

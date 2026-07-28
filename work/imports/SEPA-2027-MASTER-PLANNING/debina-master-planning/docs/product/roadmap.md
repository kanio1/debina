---
title: "Debina Product and Architecture Roadmap"
document_id: DMP-ROADMAP-001
status: DRAFT
document_role: ROADMAP_PROPOSAL
baseline_repository: kanio1/debina
baseline_branch: main
baseline_commit: f601089d2f123ff01f41378f47be5dd9ce361fd2
created_at: 2026-07-27
last_reviewed_at: 2026-07-27
owner: project-owner
canonical_sources:
  - planning/programs/DEBINA-ENTERPRISE-REBASE-PROGRAM.md
  - docs/product/debina-capability-map.md
  - docs/architecture/current-state.md
  - docs/architecture/target-state.md
supersedes: []
does_not_supersede:
  - AGENTS.md
  - README.md
  - accepted ADRs
  - planning/epics
  - planning/capability-graph.json
  - approved Use-Case 2.0 records
confidence: medium
---

# 1. Purpose

Ustawić kolejność pracy według wartości, dowodów i zależności, bez tworzenia szczegółowego planu odległych funkcji.

# 2. Scope

Horizon 0-4 od governance do opcjonalnych rozszerzeń.

# 3. Non-goals

- daty delivery bez capacity data;
- masowe stories;
- parallel implementation wielu schematów/raili;
- automatyczne priorytetyzowanie przez agenta.

# 4. Current verified facts

Enterprise rebase wymaga governance, UC catalogue, architecture evidence, smoke, semantic CI i backlog migration przed feature delivery.

# 5. Assumptions and open questions

- Horizon oznacza kolejność logiczną, nie kwartał.
- SDD wymaga decyzji produktu.
- 2027 final sources mogą zmienić Horizon 2.

# 6. Main content

## Horizon 0 - Governance and evidence

**Outcome:** przyjęta warstwa syntezy, spójny baseline, source-to-code pilot i bezpieczny workflow.

**Prerequisites:** lokalne porównanie z baseline.

**Capabilities:** source registry, claim map, UC2 method, C4 evidence, semantic planning, documentation runway.

**Entry:** master package reviewed.

**Exit:**

- konflikt Wave 11 rozwiązany;
- product/capability/current/target docs zaakceptowane;
- top 3 flow cohort wybrany;
- source hash mismatch wyjaśniony;
- work queue nie dubluje planning.

**Decision gates:** DR-001, DR-010, DR-012.

## Horizon 1 - Stabilize existing SCT/SCT Inst spine

**Outcome:** istniejący credit-transfer flow ma kompletne UC2 slices, wyjątki, egress/receipt/retry/reconciliation granice i smoke evidence.

**Focus:**

- submit/approval/detail pilot;
- incoming status correlation;
- unknown/timeout/restoration;
- egress delivery attempts/receipts;
- reconciliation foundation;
- R-process catalogue bez pełnej automatyzacji cases;
- rule/message profile version governance.

**Entry:** Horizon 0 exit, executable READY slice.

**Exit:** selected end-to-end flows trace to runtime evidence; no hidden owner/source blockers.

## Horizon 2 - SEPA 2027

**Outcome:** source-backed readiness plan i wybrane laboratoryjne capabilities dla Instant Payments Regulation, VOP i EDS.

**Focus:**

- legal applicability model;
- VOP Requesting/Responding PSP UC2;
- EDS snapshot lifecycle;
- addresses;
- user limits/bulk parity/pricing decision;
- restrictive measures screening model;
- November 2027 rule cutover.

**Entry:** public final artefacts acquired, DR-002/003/007 accepted.

**Exit:** contracts validated, effective dates governed, synthetic evidence explicit; no compliance claim.

## Horizon 3 - SDD Core after product decision

**Outcome:** accepted domain model and staged roadmap for mandate, collection, R/claims, reporting and reconciliation.

**Entry:** DR-004 accepted, legal/reachability scope, complete public EPC baseline.

**Exit:** top SDD UC2 catalogue, module/aggregate admissions, contracts/gaps, one small READY slice.

## Horizon 4 - Optional extensions

- SDD B2B;
- e-Mandate;
- STEP2 adapter after participant evidence;
- additional rail adapters;
- optional VOP datasets;
- PG19 lab promotions after GA and evidence.

# 7. Relationships to existing artifacts

Roadmapa rozwija rebase program, ale nie zmienia epików ani ADR-N6 priority taxonomy.

# 8. Risks

- Horizon 2 rozpocznie się przed stabilizacją spine;
- SDD stanie się wielkim programem bez product decision;
- źródła 2027 zmienią się;
- brak participant docs uniemożliwi rail fidelity.

# 9. Evidence and canonical sources

Capability map, traceability, decisions required, enterprise rebase.

# 10. Review triggers

Exit każdego horizon, final source publication, product decision, capacity/priority change.

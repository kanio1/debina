---
title: "Debina Documentation Backlog"
document_id: DMP-DOC-BACKLOG-001
status: DRAFT
document_role: ROADMAP_PROPOSAL
baseline_repository: kanio1/debina
baseline_branch: main
baseline_commit: f601089d2f123ff01f41378f47be5dd9ce361fd2
created_at: 2026-07-27
last_reviewed_at: 2026-07-27
owner: project-owner
canonical_sources:
  - docs/product/roadmap.md
  - planning/programs/DEBINA-ENTERPRISE-REBASE-PROGRAM.md
  - docs/requirements/USE-CASE-METHOD.md
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

Ustawić dokumentacyjny runway przed implementacją i uniknąć duplikowania artefaktów już istniejących.

# 2. Scope

Business Process/Use Case/Slices, Flow Specs, RFC/ADR, C4, contracts, runbooks, smoke i traceability.

# 3. Non-goals

- liczba dokumentów jako KPI;
- pełny backfill historii;
- portal dokumentacyjny;
- external knowledge-base or memory dependency.

# 4. Current verified facts

Repo ma mocne metody i backlog, ale brakuje zaakceptowanej syntezy oraz małego kohortu z pełnym trace.

# 5. Assumptions and open questions

Priorytety są propozycją do human review.

# 6. Main content

| Bucket | Artefakt/work item | Outcome | Existing source | Status |
|---|---|---|---|---|
| NOW | Baseline reconciliation | Update HANDOFF/Wave11/program status after local evidence check | planning/HANDOFF | DOCUMENTATION_STALE |
| NOW | Accept product/capability/current/target synthesis | Review this package and record decisions | new docs | DRAFT |
| NOW | Top UC2 pilot | Submit payment -> maker-checker -> payment detail/evidence | existing EPIC-19/24/26/76/77/78 | DISCOVERY |
| NOW | Source integrity | Regenerate/check corpus checksums; acquire listed public artefacts | external corpus | BLOCKED/PARTIAL |
| NOW | Architecture evidence pack | Review context/container; add deployment and 1-2 dynamic diagrams | C4 requirements | PARTIAL |
| NEXT | Scheme rule registry RFC | Compare reference-data extension vs new module | DR-007 | DECISION_REQUIRED |
| NEXT | Minimal smoke specification | API/runtime smoke, no Playwright in this phase | ADR-N16/rebase D | DISCOVERY |
| NEXT | Traceability validator pilot | Validate source/use-case/story/test links for small cohort | rebase E/F | DISCOVERY |
| NEXT | SCT/SCT Inst exception flows | Business/System UC and slices for reject/return/recall/unknown | EPIC-30/65.. | SOURCE_REVIEW |
| NEXT | VOP/EDS discovery | Acquire API YAML/EDS docs, write Business/System UCs | corpus gaps 4/5/10 | SOURCE_BLOCKED |
| LATER | Reconciliation use cases | Settlement-vs-ledger, drift, mismatch, escalation | EPIC-57..64 | SPECIFIED_ONLY |
| LATER | Case/R lifecycle | R catalogue, evidence, decision/action, duplicates | EPIC-65..72 | SPECIFIED_ONLY |
| LATER | SDD Core catalogue | Mandate, collection, R, refunds, claims, reporting | DR-004 | DECISION_REQUIRED |
| BLOCKED | STEP2 SDD adapter docs | Participant manuals, SATP, certification pack | gaps 6-9 | PARTICIPANT_DOC_REQUIRED |
| BLOCKED | Final 2027 delta | Final rulebooks/IG/VOP v2 | gaps 1/10 | FUTURE_NOT_FINAL |
| ALREADY_EXISTS | Source authority | Topic-specific hierarchy and evidence tags | docs/standards | KEEP |
| ALREADY_EXISTS | Use-Case 2.0 method | Delivery hierarchy and required fields | docs/requirements | KEEP |
| ALREADY_EXISTS | Architecture admission | C4/context/quality/ATAM-lite method | docs/architecture | KEEP |
| ALREADY_EXISTS | Executable backlog | 76 epics and capability graph | planning/ | KEEP |
| NOT_NEEDED | Mass rewrite of 76 epics | Incremental migration only | rebase F | DO_NOT_DO |

## Documentation runway rule

- 0-1 item `IMPLEMENTING`;
- 1 item `READY` po human approval;
- 1-2 items `DISCOVERY`;
- maksymalnie 3 `CANDIDATES`;
- odległe horizons pozostają capability-level.

## Definition of Ready dla dokumentacji->implementacji

- source authority i applicable scope;
- Business/System UC + slice;
- rules/invariants/alternatives/failures;
- owner/data boundary;
- contract/migration impact;
- quality scenarios/observability;
- test strategy i executable verify;
- decyzje i evidence gaps zamknięte albo jawnie out of slice;
- human approval.

# 7. Relationships to existing artifacts

Nowe artefakty linkują do `planning/`; nie powstaje równoległa numeracja epików.

# 8. Risks

- utrzymywanie zbyt dużego runway;
- stale docs przed implementacją;
- ADR dla każdej drobnej decyzji;
- Flow Spec kopiujący OpenAPI/AsyncAPI fields.

# 9. Evidence and canonical sources

Roadmapa, UC method, rebase program, findings.

# 10. Review triggers

Zmiana kolejki, zakończenie slice, nowe source gap, review co 2-4 tygodnie aktywnej pracy.

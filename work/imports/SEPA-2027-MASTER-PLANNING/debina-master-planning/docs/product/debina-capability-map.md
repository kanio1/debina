---
title: "Debina Capability Map"
document_id: DMP-CAPABILITY-MAP-001
status: DRAFT
document_role: CAPABILITY_SYNTHESIS
baseline_repository: kanio1/debina
baseline_branch: main
baseline_commit: f601089d2f123ff01f41378f47be5dd9ce361fd2
created_at: 2026-07-27
last_reviewed_at: 2026-07-27
owner: project-owner
canonical_sources:
  - planning/README.md
  - planning/capability-graph.json
  - planning/epics/
  - docs/requirements/USE-CASE-METHOD.md
  - external-corpus:README.md
  - external-corpus:12_ANALYSIS/2027-scope-decision.md
  - external-corpus:12_ANALYSIS/2027-readiness-report.md
  - external-corpus:12_ANALYSIS/2027-capability-matrix.csv
  - external-corpus:12_ANALYSIS/r-messages-and-claims-matrix.csv
  - external-corpus:12_ANALYSIS/source-register-2027.csv
  - external-corpus:12_ANALYSIS/coverage-gaps-2027.csv
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

Zsyntetyzować capabilities istniejącej Debiny i korpusu SEPA 2027 bez zmiany epików, numeracji lub zależności.

# 2. Scope

Capabilities biznesowe, domenowe, platformowe i dowodowe potrzebne do planowania kolejnych slice'ów.

# 3. Non-goals

- automatyczne tworzenie epików;
- stwierdzanie compliance;
- uznanie każdej capability 2027 za obowiązkowy zakres Debina;
- kopiowanie historycznych story IDs z `capabilities.yaml` jako aktualnych.

# 4. Current verified facts

Aktualnymi źródłami story IDs i zależności są epiki oraz `planning/capability-graph.json`. Capability YAML zachowuje także historyczny pre-split inventory.

# 5. Assumptions and open questions

- Statusy repo są konserwatywnym snapshotem zdalnego `main`.
- VOP/EDS/SDD nie mają jeszcze repo source-to-code audit.
- Kandydat owner nie oznacza module admission.

# 6. Main content

## 6.1 Project-native capability groups

| Grupa | Aktualny stan | Główne artefakty |
|---|---|---|
| Repository, identity, CI, walking skeleton | EXISTS | EPIC-00..08 |
| Ownership and architecture enforcement | PARTIAL/EVOLVING | EPIC-09..18 |
| Payment initiation and lifecycle spine | PARTIAL | EPIC-19..25 |
| ISO lineage, validation, correlation | PARTIAL, lineage strong | EPIC-26..30 |
| Signature | EXISTS | EPIC-31 |
| Ledger and settlement | PARTIAL, several strong slices | EPIC-32..42 |
| Egress and delivery | PARTIAL | EPIC-43..50 |
| Routing | PARTIAL | EPIC-51..56 |
| Reconciliation | SPECIFIED_ONLY | EPIC-57..64 |
| Cases/R-transactions | SPECIFIED_ONLY | EPIC-65..72 |
| File rail/security/docs/approval/audit/GraphQL | MIXED | EPIC-73..78 |

## 6.2 SEPA 2027 capability overlay

| ID | Domain | Capability | Obligation class | Priority | Repo status | Candidate owner | Existing links | Primary evidence | Assessment |
|---|---|---|---|---|---|---|---|---|---|
| CAP-001 | SCT | Complete credit-transfer lifecycle including rejects, returns, recalls and inquiries | SCHEME MANDATORY IF SCT | P0 | PARTIAL | payment-lifecycle, iso-adapter, egress, case | EPIC-19..30, EPIC-43..49, EPIC-65..72 | EPC SCT 2025 rulebook/IG | SCT happy path/lineage istnieje; pełny R/recall/inquiry lifecycle nie. |
| CAP-002 | SCT Inst | Receive instant euro transfers | LEGAL DEADLINE — covered PSP in non-euro Member State | P0 | PARTIAL | payment-lifecycle, settlement, routing | EPIC-20, 33, 35, 39, 51..56 | EPC SCT Inst 2025 + EU-REG-2024-886 | Model instant istnieje częściowo; 24/7 operational readiness nieudowodniona. |
| CAP-003 | SCT Inst | Send instant euro transfers | LEGAL DEADLINE — covered PSP in non-euro Member State | P0 | PARTIAL | ingress, routing, settlement, egress | EPIC-19, 33, 43..56 | EPC SCT Inst 2025 + EU-REG-2024-886 | Brak kompletnego egress/retry/receipt i wszystkich kanałów. |
| CAP-004 | SCT Inst | Equality of charges | LEGAL DEADLINE | P0 | MISSING | product/pricing (owner undecided) | none | EPC SCT Inst 2025 + EU-REG-2024-886 | Wymaga decyzji zakresowej; nie wynika z obecnych modułów. |
| CAP-005 | SCT Inst | Bulk/package parity | LEGAL IF ORDINARY BULK CREDIT TRANSFERS ARE OFFERED | P0 | MISSING | batch/ingress (candidate) | EPIC-73 plus future use case | EPC SCT Inst 2025 + EU-REG-2024-886 | Brak bulk parity i file rail. |
| CAP-006 | SCT Inst | User-configurable instant limit | LEGAL | P0 | MISSING | risk/reference-data or new capability | none | EPC SCT Inst 2025 + EU-REG-2024-886 | Brak limit management. |
| CAP-007 | SCT Inst | Ten-second result and payer-account restoration path | LEGAL | P0 | PARTIAL | payment-lifecycle, settlement, egress, reconciliation | EPIC-20, 33, 46, 47, 57..64 | EPC SCT Inst 2025 + EU-REG-2024-886 | Unknown/timeout/restoration i późniejsze recon niekompletne. |
| CAP-008 | Restrictive measures | Targeted financial-restrictive-measures screening | LEGAL FOR PSP OFFERING INSTANT TRANSFERS | P0 | MISSING | risk (candidate) | no implemented owner | EU-REG-2024-886 | Wymaga legal/effective snapshot i audit use case. |
| CAP-009 | VOP | Verification before payer authorisation for standard and instant credit transfers | LEGAL DEADLINE | P0 | MISSING | VOP bounded context candidate | none | EU-REG-2024-886 + EPC-VOP-* | Brak UC, API YAML i runtime. |
| CAP-010 | VOP | Requesting PSP and Responding PSP roles | LEGAL + EPC SCHEME | P0 | MISSING | VOP bounded context candidate | none | EU-REG-2024-886 + EPC-VOP-* | Role Requesting/Responding nie istnieją. |
| CAP-011 | EDS | Directory and endpoint-data ingestion | EPC OPERATIONAL DEPENDENCY FOR VOP | P0 | MISSING | reference-data or EDS capability candidate | none | EPC-EDS-* | Brak plików/API/credentials i activation lifecycle. |
| CAP-012 | SDD Core | Consumer direct-debit reachability | CONDITIONAL LEGAL REQUIREMENT | P0 | CONDITIONAL | product decision | none | EPC-SDD-CORE-* | Warunek prawny i zakres produktu niepotwierdzone. |
| CAP-013 | SDD Core | Direct-debit mandate and collection lifecycle | SCHEME MANDATORY IF SDD CORE IS OFFERED/PARTICIPATED | P0 | MISSING | SDD mandate/collection candidate | none | EPC-SDD-CORE-* | Wymaga produktu i source-backed UC2. |
| CAP-014 | SDD B2B | Business-to-business direct debit | OPTIONAL SCHEME/PRODUCT | P1 | OPTIONAL | SDD B2B candidate | none | EPC-SDD-B2B-* | Odłożyć do osobnej decyzji. |
| CAP-015 | SDD | Mandate storage and transmission of mandate-related data | LEGAL + SCHEME IF SDD | P0 | MISSING | SDD mandate candidate | none | EPC SDD sources | Brak immutable mandate lifecycle. |
| CAP-016 | SDD payer controls | Amount/periodicity limits and creditor allow/block lists | LEGAL FOR CONSUMER DIRECT DEBITS | P0 | MISSING | SDD controls/risk candidate | none | EU/SDD legal evidence | Brak account-level controls. |
| CAP-017 | SDD | Structured or hybrid postal addresses | SCHEME MANDATORY | P0 | PARTIAL | iso-adapter/reference-data | EPIC-28 plus future rule registry | EPC SDD sources | Guidance istnieje, pełne versioned validation nie. |
| CAP-018 | SDD | Refusal | SCHEME MANDATORY IF SDD | P0 | MISSING | case/SDD lifecycle candidate | EPIC-65..72 only SCT-oriented future | EPC SDD sources | Nie utożsamiać refusal z reject. |
| CAP-019 | SDD | Reject | SCHEME MANDATORY IF SDD | P0 | MISSING | case/SDD lifecycle candidate | EPIC-65..72 future | EPC SDD sources | Brak file/group/transaction granularity. |
| CAP-020 | SDD | Return | SCHEME MANDATORY IF SDD | P0 | MISSING | case, settlement, ledger, reporting | EPIC-42, 65..72 future | EPC SDD sources | Brak SDD return/accounting flow. |
| CAP-021 | SDD Core | Authorised refund right | LEGAL/SCHEME IF SDD CORE | P0 | MISSING | case/SDD Core | none | EPC-SDD-CORE-* | Brak 8-week authorised refund workflow. |
| CAP-022 | SDD Core | Unauthorised direct-debit refund claim | LEGAL/SCHEME IF SDD CORE | P0 | MISSING | case/evidence-audit/SDD Core | none | EPC-SDD-CORE-* | Brak DS-08/DS-09 i 13-month claim lifecycle. |
| CAP-023 | SDD B2B | B2B refund/dispute model | SCHEME MANDATORY IF SDD B2B | P0 | MISSING | case/SDD B2B | none | EPC-SDD-B2B-* | Wymaga odrębnej semantyki B2B. |
| CAP-024 | SDD | Reversal | SCHEME MANDATORY IF SDD | P0 | MISSING | case/SDD lifecycle | none | EPC SDD sources | Brak pain.007/pacs.007 flow. |
| CAP-025 | SDD | Request and provision of mandate copy | SCHEME MANDATORY WHEN CASE IS RAISED | P0 | MISSING | case/evidence-audit | none | EPC SDD sources | Brak DS-10/DS-11 neutral evidence exchange. |
| CAP-026 | SDD reporting | Customer reporting and reconciliation | OPERATIONAL/SCHEME SUPPORT | P0 | MISSING | reporting/reconciliation | EPIC-57..64 future | EPC-CUSTOMER-REPORTING-V5 | Current operational views nie są customer reporting. |
| CAP-027 | SDD | Creditor Identifier validation | SCHEME MANDATORY IF SDD | P0 | MISSING | reference-data/SDD | none | EPC SDD sources | Brak country-specific validation registry. |
| CAP-028 | SDD | e-Mandate service | OPTIONAL SERVICE | P2 | OPTIONAL | future e-Mandate capability | none | EPC SDD sources | Nie planować bez produktu/legal evidence. |
| CAP-029 | STEP2 SDD | STEP2 Core/B2B rail adapters | ONLY IF STEP2 IS SELECTED; PARTICIPANT-DOC DEPENDENT | P1 | BLOCKED_BY_EVIDENCE | rail adapter candidate | none | STEP2-SDD-* participant evidence | Participant manuals, SATP i certification evidence missing. |
| CAP-030 | Rule management | November 2027 EPC cutover | FUTURE FINAL PUBLICATION REQUIRED | P0 | MISSING | reference-data / scheme-rule registry candidate | rebase governance; no delivery epic | EPC future publications | Kluczowy enabler dla dual-run/cutover. |
| CAP-031 | VOP | Optional post-VOP inquiry/refund dataset exchange | OPTIONAL EPC GUIDANCE | P2 | OPTIONAL | VOP future | none | EU-REG-2024-886 + EPC-VOP-* | Oddzielić od mandatory VOP. |

## 6.3 Admission policy

Status `MISSING` nie oznacza automatycznie nowego modułu. Kolejność:

1. source-backed Business/System Use Case;
2. use-case slices;
3. existing-owner fit analysis;
4. public port/data ownership design;
5. module/aggregate admission, gdy kryteria są spełnione;
6. RFC/ADR tylko dla istotnej decyzji;
7. story/task/test/runtime evidence.

# 7. Relationships to existing artifacts

- `planning/` pozostaje wykonywalnym backlogiem.
- Ta mapa służy do wyboru discovery i wykrywania luk.
- Statusy nie zmieniają frontmatterów epików.

# 8. Risks

- owner candidate potraktowany jako zaakceptowany moduł;
- legal obligation przypisana syntetycznemu projektowi bez product decision;
- brak finalnych 2027 źródeł;
- masowe tworzenie stories dla wszystkich 31 pozycji.

# 9. Evidence and canonical sources

Repo: planning index/graph/epics. Korpus: capability matrix, scope/readiness, source register.

# 10. Review triggers

Repo implementation audit, finalne EPC 2027, product decision VOP/EDS/SDD, nowy/superseding ADR.

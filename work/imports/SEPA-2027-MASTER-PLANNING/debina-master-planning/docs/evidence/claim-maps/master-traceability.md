---
title: "Debina Master Traceability"
document_id: DMP-TRACEABILITY-001
status: DRAFT
document_role: TRACEABILITY_INDEX
baseline_repository: kanio1/debina
baseline_branch: main
baseline_commit: f601089d2f123ff01f41378f47be5dd9ce361fd2
created_at: 2026-07-27
last_reviewed_at: 2026-07-27
owner: project-owner
canonical_sources:
  - docs/standards/SOURCE-AUTHORITY-MATRIX.md
  - docs/requirements/USE-CASE-METHOD.md
  - planning/epics/
  - planning/capability-graph.json
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

Zapewnić nawigacyjny indeks source claim -> requirement/use case -> architecture -> delivery evidence.

# 2. Scope

Najważniejsze istniejące i przyszłe claims. To pilot traceability, nie pełny backfill 76 epików.

# 3. Non-goals

- fikcyjne ogniwa;
- kopiowanie rulebook text;
- certyfikacja;
- zastąpienie dokładnych trace records w use cases/stories.

# 4. Current verified facts

Repo ma wiele lokalnych evidence chains, ale brak jednej master mapy i brak audit dla VOP/EDS/SDD.

# 5. Assumptions and open questions

Status `PARTIAL` oznacza, że co najmniej jedno ogniwo nie jest kompletne albo aktualne.

# 6. Main content

| Source/tag | Claim/rule | Use case | Decision/realization | Owner/module | Contract/schema | Epic/story | Test/runtime evidence | Status | Gap |
|---|---|---|---|---|---|---|---|---|---|
| PROJECT-ADR | ADR-N15 enterprise research scope | All use cases | AGENTS/vision | All modules | N/A | All epics | Governance review | VERIFIED | No compliance claim |
| PROJECT-ADR | one-writer-per-schema | Cross-cutting quality UC | ADR/frozen architecture | All persistence ports | SQL grants/migrations | EPIC-09..18 | ArchUnit + real-role DB tests | PARTIAL | Rollout follows module existence |
| PROJECT-ADR | LedgerPort-only money path | Settlement/ledger UCs | ADR-N10 | settlement -> LedgerPort -> ledger | ledger schema | EPIC-32..42 | PostgreSQL/mutation proofs | PARTIAL | Reverse command remains source-blocked |
| PROJECT-ADR | explicit finality; delivered != final | Settlement/finality UC | ADR-N10/N13 | settlement/egress | finality catalog/authority | EPIC-39,47 | negative boundary tests | VERIFIED/PARTIAL | Receipt lifecycle incomplete |
| PROJECT-ADR | Query-only GraphQL | Operational read UCs | ADR-N17 | graphql -> source query ports | GraphQL schema | EPIC-78,24,26 | structure/auth/runtime tests | VERIFIED/PARTIAL | Demand-driven only |
| EPC-SCT | pain.001 customer initiation | Submit payment instruction | existing slice | ingress/iso/payment | OpenAPI + ISO mappings | EPIC-19,26 | endpoint + DB + runtime proof | VERIFIED | Bank channel constraints separate |
| EPC-SCT | pacs.002/status correlation | Process incoming status | partial/future slice | iso/payment | AsyncAPI/ISO mapping | EPIC-20,27 | correlation tests | PARTIAL | Real inbound consumer missing |
| EPC-SCT | reject/return/recall/inquiry lifecycle | R-process catalogue | missing UC2 cohort | case/payment/egress | future contracts | EPIC-30,49,65..72 | none complete | MISSING | Source-backed rules needed |
| EU-REG-2024-886 | receive/send instant euro deadlines | Instant payment capabilities | future UC2 | payment/routing/settlement/egress | future contracts | related existing epics | selected component tests | PARTIAL | Legal applicability/product role must be stated |
| EU-REG-2024-886 | ten-second result/restoration | Instant outcome UC | missing | payment/egress/recon | future | future slice | none end-to-end | MISSING | Unknown != reject |
| EU-REG-2024-886 | VOP before authorisation | VOP Requesting PSP UC | missing | owner admission required | EPC VOP API | none | none | SOURCE_BLOCKED | API YAML not local |
| EPC-VOP-* | Responding PSP and match outcomes | VOP Responding PSP UC | missing | owner admission required | EPC VOP API | none | none | SOURCE_BLOCKED | Final v2 future change |
| EPC-EDS-* | EDS snapshot ingestion/activation | EDS operational UC | missing | reference-data candidate | EDS files/API | none | none | SOURCE_BLOCKED | Credentials/files/onboarding missing |
| EPC-ADDRESS-GUIDANCE | no unstructured-only address after 2026-11-15 | Address validation rule | missing/partial | iso/reference-data | message validation profiles | EPIC-28/future | partial | PARTIAL | Version-aware migration needed |
| EPC future 2027 | November 2027 cutover | Rule cutover UC | missing | reference-data candidate | rule/message/profile registry | none | none | FUTURE | Final publications unavailable |
| EPC-SDD-CORE-* | SDD Core mandate/collection | SDD Core UCs | missing | owner admission required | pain.008/pacs.003 etc. | none | none | SOURCE_BLOCKED | Product decision + docs missing |
| EPC-SDD-* | refusal/reject/return/refund/reversal | SDD R/claims UCs | missing | case/SDD/settlement/ledger | future contracts | none | none | MISSING | Do not collapse processes |
| EPC-CUSTOMER-REPORTING-V5 | camt.052/053/054 reporting | Customer reporting UC | missing | reporting/reconciliation | reporting profiles | future | none | SOURCE_BLOCKED | Source not local |
| STEP2-SDD-* | STEP2 envelopes/cycles/ACK/finality | STEP2 adapter UC | missing | rail adapter candidate | participant contract | none | none | SOURCE_BLOCKED | Participant docs/SATP required |
| BANK-PRACTICE | Nordea/BOI/ING/SEB channel profiles | Bank channel profile UC | missing | reference-data/channel adapter | effective-dated profile | none | none | PARTIAL_EVIDENCE | Never universalize |

## Traceability policy

1. Twierdzenie standardowe ma source ID, version, effective date i section/page.
2. Business rule wskazuje applicable scheme/rail/channel.
3. Use-case slice jest najmniejszym delivery unit.
4. Architecture realization wskazuje owner, port, data boundary i quality scenario.
5. Epic/story wykonuje slice; nie zastępuje requirement.
6. Test i runtime evidence mają dokładną komendę oraz wynik.
7. Brak ogniwa jest statusem, nie powodem do jego wymyślenia.

# 7. Relationships to existing artifacts

Szczegóły pozostają w source registry, UC records, ADR, epics, contracts, migrations i testach.

# 8. Risks

- master table szybko się starzeje;
- status `VERIFIED` użyty dla zbyt szerokiego claim;
- bank practice i rail authority pomieszane;
- future source aktywowany przed effective date.

# 9. Evidence and canonical sources

Zobacz frontmatter i `SOURCE-COVERAGE.md`.

# 10. Review triggers

Zmiana claim/source/version, przyjęcie UC slice, implementacja/test evidence, finalne 2027 publikacje.

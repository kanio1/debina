---
title: "Debina Current State"
document_id: DMP-CURRENT-STATE-001
status: DRAFT
document_role: CURRENT_STATE_SNAPSHOT
baseline_repository: kanio1/debina
baseline_branch: main
baseline_commit: f601089d2f123ff01f41378f47be5dd9ce361fd2
created_at: 2026-07-27
last_reviewed_at: 2026-07-27
owner: project-owner
canonical_sources:
  - AGENTS.md
  - HANDOFF.md
  - README.md
  - planning/README.md
  - planning/AGENTS.md
  - planning/programs/DEBINA-ENTERPRISE-REBASE-PROGRAM.md
  - planning/capabilities.yaml
  - planning/capability-graph.json
  - docs/requirements/USE-CASE-METHOD.md
  - docs/architecture/ARCHITECTURE-METHOD.md
  - docs/architecture/C4-REQUIREMENTS.md
  - docs/architecture/CONTEXT-MAP.md
  - docs/domain/AGGREGATE-ADMISSION-RULES.md
  - docs/standards/SOURCE-AUTHORITY-MATRIX.md
  - planning/epics/
  - planning/programs/DEBINA-ISO-LINEAGE-IDENTIFIER-EVIDENCE-WAVE-11.md
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

Opisać wyłącznie zweryfikowany zdalny stan Debina na baseline commit i wyraźnie oddzielić implementację od specyfikacji.

# 2. Scope

Architektura, główne moduły/capabilities, test evidence, governance i aktywny stan planowania.

# 3. Non-goals

- lokalny working tree;
- target architecture;
- potwierdzenie pełnego compliance;
- pełny class/package inventory.

# 4. Current verified facts

## 4.1 Baseline

- Repository: `kanio1/debina`
- Branch: `main`
- Commit: `f601089d2f123ff01f41378f47be5dd9ce361fd2`
- Commit intent: enterprise payment constitution/governance rebase.

## 4.2 Status overview

| Obszar | Status | Planning evidence | Snapshot |
|---|---|---|---|
| Platform foundation | IMPLEMENTED_AND_VERIFIED | EPIC-00..08 | Repo, PG18, Keycloak, Spring Modulith, Kafka thin, BFF/UI, CI walking skeleton. |
| Ingress | IMPLEMENTED_AND_VERIFIED | EPIC-19 | JSON_DIRECT i signed pain.001 canonical intake. |
| Payment lifecycle | PARTIAL | EPIC-20, 22, 76 | Podstawowy FSM/approval; nie pełny SCT/SCT Inst/R lifecycle. |
| ISO adapter/lineage | IMPLEMENTED_AND_VERIFIED / PARTIAL | EPIC-21, 26..30 | Lineage i identifiers strong; correlation/validation/outbound/R lineage częściowe. |
| Signature | IMPLEMENTED_AND_VERIFIED | EPIC-31 | Schema/ports/Ed25519/public boundary. |
| Ledger | PARTIAL | EPIC-32 | Core/reservations/immutability; reverse boundary source-blocked. |
| Settlement | PARTIAL | EPIC-33..42 | Gross instant, deferred cycles i finality częściowo/znacznie udowodnione; nie wszystkie profile. |
| Egress | PARTIAL | EPIC-43..50 | Outbound dispatcher istnieje; render/profile/retry/receipt niekompletne. |
| Routing | PARTIAL | EPIC-51..56 | Resolution/eligibility/decision/fallback slices istnieją; source/capability gaps pozostają. |
| Reconciliation | SPECIFIED_ONLY | EPIC-57..64 | Backlog istnieje, implementation nie. |
| Case/R-transactions | SPECIFIED_ONLY | EPIC-65..72 | Backlog istnieje, implementation nie. |
| Reference data | PARTIAL | EPIC-12, 35, 39 | Schema/catalog slices; brak pełnego effective-dated scheme registry. |
| Identity/security | IMPLEMENTED_AND_VERIFIED / PARTIAL | EPIC-02, 74 | Keycloak baseline/runtime proof; P1 controls pozostają. |
| Evidence/audit | PARTIAL | EPIC-77 | Append-only command log/query boundary częściowo. |
| GraphQL technical adapter | PARTIAL | EPIC-78, ADR-N17 | Query-only operational reads, fixed BFF operations; demand-driven. |
| Frontend/BFF | PARTIAL | EPIC-23, 24 | Foundation i kilka ekranów/workspaces; nie pełny katalog. |
| Observability | PARTIAL | EPIC-25, 50, 64, 72 | Lag baseline; retry/DLQ/alerts i future modules niekompletne. |
| Simulation | SPECIFIED_ONLY | EPIC-17 + open question | Boundary enforcement planned; engine owner/backlog gap. |
| Reporting | SPECIFIED_ONLY | EPIC-16/related future | Operational read != customer reporting; brak pełnego module implementation. |
| Dagger semantic CI | PROPOSED | Rebase Phase E | Architecture/design exists; implementation must be verified locally. |

# 5. Assumptions and open questions

- `[CONFLICT]` `HANDOFF.md`/Wave 11 record vs EPIC-26/planning index.
- `[OPEN-QUESTION]` Dagger implementation status wymaga lokalnej weryfikacji komend i CI.
- `[OPEN-QUESTION]` Local branch może zawierać nowszy stan.
- `[OPEN-QUESTION]` Nie wszystkie planning statusy potwierdzono ponownym uruchomieniem testów w tej analizie.

# 6. Main content

## 6.1 Runtime shape

```text
Browser/UI -> Next.js BFF -> REST commands / allowlisted GraphQL queries
                               |
                        Spring Modulith
                               |
            PostgreSQL schemas + Kafka outbox/inbox
                               |
                       Keycloak identity
```

## 6.2 Strong current invariants

- one-writer-per-schema;
- selective RLS/ownership grants;
- LedgerPort-only money movement;
- settlement-owned explicit finality;
- transport and receipt do not imply finality;
- reconciliation detects/escalates, never repairs;
- Query-only GraphQL and source-owned read ports;
- no invented optional identifiers;
- tests include real PostgreSQL/Kafka/Keycloak evidence for selected slices.

## 6.3 Verified user/system journeys

- JSON_DIRECT payment submission;
- signed pain.001 submission with ISO lineage and identifiers;
- maker-checker prefix gate/approval slices;
- payment detail operational reads;
- evidence/audit and ISO evidence through Query-only GraphQL/BFF;
- selected settlement, routing, ledger and outbox proofs.

## 6.4 Major incomplete areas

- complete SCT/SCT Inst exception, retry, receipt and reconciliation lifecycle;
- actual customer/rail reporting;
- case/R-message execution;
- full egress render/delivery lifecycle;
- simulation engine;
- SEPA 2027 VOP/EDS/restrictive-measures capabilities;
- SDD product and lifecycle;
- final 2027 rule cutover governance.

## 6.5 Documentation state

Mocne strony:

- ADR constitution;
- capability planning;
- explicit open questions;
- test commands and runtime evidence;
- source authority and admission methods.

Problemy:

- część starych opisów utrzymuje nieaktualne blockers/status;
- planning summaries i handoff mogą się rozjechać;
- brakuje product/capability/current/target synthesis;
- source-to-code traceability nie obejmuje SEPA 2027.

# 7. Relationships to existing artifacts

Dokument jest snapshotem. `planning/README.md`, epiki, kod i testy pozostają bardziej szczegółowe i kanoniczne.

# 8. Risks

- snapshot starzeje się po każdym commicie;
- planning status nie zawsze oznacza runtime readiness;
- zdalny GitHub nie pokazuje lokalnych zmian;
- częściowe capabilities mogą wyglądać jak kompletne z powodu wielu zielonych testów.

# 9. Evidence and canonical sources

Zobacz frontmatter, `findings.md` i `SOURCE-COVERAGE.md`.

# 10. Review triggers

Każdy większy merge, zakończenie rebase phase, zmiana module boundary, finalne SEPA 2027 źródło.

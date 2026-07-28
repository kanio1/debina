---
title: "Debina Master Planning - Decisions Required"
document_id: DMP-DECISIONS-001
status: DRAFT
document_role: DECISION_REGISTER
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
confidence: high
---

# 1. Purpose

Zebrać decyzje, których agent nie może podjąć samodzielnie, i oddzielić je od zwykłych zadań discovery/implementation.

# 2. Scope

Decyzje produktowe, architektoniczne, dowodowe i operacyjne wpływające na kolejność enterprise rebase oraz SEPA 2027.

# 3. Non-goals

- automatyczne zatwierdzanie;
- tworzenie ADR bez decyzji właściciela;
- nadawanie `READY` stories z otwartymi gate'ami.

# 4. Current verified facts

- Feature expansion jest wstrzymany.
- Source authority i frozen ADR są wiążące.
- Część dokumentów Wave 11 jest niespójna statusowo.
- VOP/EDS/SDD mają istotne braki dowodowe.

# 5. Assumptions and open questions

Każda pozycja poniżej pozostaje otwarta do jawnej decyzji właściciela projektu.

# 6. Main content

| ID | Decyzja | Opcja A | Opcja B | Rekomendacja zespołu | Confidence | Reversibility | Status |
|---|---|---|---|---|---|---|---|
| DR-001 | Reconciliation Wave 11 i stanu handoff | Zamknąć jako completed i zaktualizować HANDOFF/program record | Wznowić brakujące proofy | Najpierw lokalnie zweryfikować commit/test evidence, następnie ujednolicić dokumenty | Wysoka | Odwracalna dokumentacyjnie | HUMAN_DECISION_REQUIRED |
| DR-002 | Zakres produktu SEPA 2027 | Modelować prawne capabilities jako scenariusze badawcze | Rozszerzyć produkt do pełnego bankowego hubu | Przyjąć capabilities jako research targets bez claims/certification | Wysoka | Product ADR required if boundary changes | HUMAN_DECISION_REQUIRED |
| DR-003 | VOP i EDS | Uruchomić discovery P0 | Odłożyć | Discovery po pozyskaniu finalnych publicznych artefaktów; bez implementacji API przed YAML/EDS evidence | Wysoka | Odwracalna do implementacji | HUMAN_DECISION_REQUIRED |
| DR-004 | SDD Core | Włączyć do roadmapy po analizie reachability/product value | Pozostawić out of scope | Utrzymać Horizon 3 i wymagać osobnej decyzji produktowej | Średnia | Wysoka przed kodem | HUMAN_DECISION_REQUIRED |
| DR-005 | SDD B2B | Włączyć jako produkt | Odłożyć jako opcjonalny | Pozostawić Horizon 4/P1, bez szczegółowych stories | Wysoka | Odwracalna | HUMAN_DECISION_REQUIRED |
| DR-006 | Zakres raili | TIPS jako publiczny research rail; STEP2/STET/RT1 jako metadata/evidence gated | Budować wszystkie adaptery równolegle | Jeden rail na raz, każdy z admission/evidence pack | Wysoka | Wysoka | HUMAN_DECISION_REQUIRED |
| DR-007 | Effective-dated scheme rule registry | Rozwinąć reference-data | Nowy moduł scheme-rules | Najpierw RFC porównujący deep capability w reference-data z nowym modułem | Średnia | Wymaga RFC/ew. ADR | HUMAN_DECISION_REQUIRED |
| DR-008 | Retencja i jurysdykcja | Polska/EU baseline z legal review | Ogólna polityka EPC | Nie kodować retencji/mandate legal validity bez legal claims approval | Wysoka | Trudna po migracjach | HUMAN_DECISION_REQUIRED |
| DR-009 | Minimalne smoke tests | Podstawowe API/runtime smoke bez Playwright | Wczesny browser smoke z ADR-N16 | W tej fazie użyć API/runtime smoke; Playwright pozostawić poza pakietem | Wysoka | Odwracalna | HUMAN_DECISION_REQUIRED |
| DR-010 | Operacyjna kolejka work/ | Dodać lekką nakładkę | Pozostać wyłącznie przy planning/ | Dodać z limitami WIP i linkami, nigdy jako drugi backlog | Wysoka | Łatwo odwracalna | HUMAN_DECISION_REQUIRED |
| DR-011 | Korpus w repo | Tylko metadane/claim maps | Commitować PDF-y | Metadane i hashe; PDF-y zewnętrznie | Wysoka | Wysoka/licencyjna | HUMAN_DECISION_REQUIRED |
| DR-012 | Kolejny pilot | Top UC2 dla submit->approval->detail | Nowa capability VOP/SDD | Najpierw istniejący, zweryfikowany flow; potem 2027 discovery | Wysoka | Łatwa | HUMAN_DECISION_REQUIRED |

## Szablon zapisu decyzji

Po decyzji użytkownika należy:

1. zapisać ją w odpowiednim artefakcie;
2. utworzyć/supersedować ADR tylko jeśli zmienia trwałą architekturę lub frozen boundary;
3. zaktualizować capability/readiness;
4. wskazać acceptance evidence i review trigger;
5. nie modyfikować historii decyzji po cichu.

# 7. Relationships to existing artifacts

- Decyzje architektoniczne: `README.md`, ADR lifecycle.
- Decyzje backlog/readiness: `planning/AGENTS.md`, epiki, capability graph.
- Luki źródłowe: `docs/evidence/claim-maps/master-traceability.md` oraz zewnętrzny gap register.

# 8. Risks

Największym ryzykiem jest potraktowanie rekomendacji jako decyzji. Wszystkie pozycje pozostają `HUMAN_DECISION_REQUIRED`.

# 9. Evidence and canonical sources

Zobacz `findings.md` i frontmatter.

# 10. Review triggers

- decyzja użytkownika;
- nowe źródło lub ADR;
- zmiana baseline commit;
- rozpoczęcie fazy F/G enterprise rebase.

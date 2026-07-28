---
title: "Debina SEPA 2027 Master Planning - Findings"
document_id: DMP-FINDINGS-001
status: DRAFT
document_role: WORKING_FINDINGS
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

Zebrać w jednym miejscu zweryfikowane fakty z repozytorium GitHub i zewnętrznego korpusu SEPA 2027, oddzielić je od wniosków oraz wskazać ryzyka przed rozpoczęciem kolejnej implementacji.

# 2. Scope

- zdalny baseline repozytorium `kanio1/debina` na wskazanym commicie;
- governance, planowanie, architektura, use cases, aktualny backlog i stan wybranych capabilities;
- korpus dowodowy SEPA 2027;
- przygotowanie płynnej ewolucji, bez migracji kodu i bez masowej przebudowy backlogu.

# 3. Non-goals

- certyfikacja zgodności;
- odtworzenie participant-only zachowań CSM;
- zmiana zamrożonych ADR;
- implementacja VOP, EDS, SDD lub nowego raila;
- zastąpienie katalogu `planning/` nową kolejką;
- analiza lokalnych, niecommitowanych zmian użytkownika.

# 4. Current verified facts

| ID | Klasyfikacja | Ustalenie | Znaczenie |
|---|---|---|---|
| F-001 | VERIFIED_FACT | Debina jest syntetyczną, enterprise-grade platformą badawczą SEPA/ISO 20022, a nie bankiem, CSM ani certyfikowanym hubem. | Każdy dokument i ekran musi zachowywać non-claims boundary. |
| F-002 | VERIFIED_FACT | Obowiązuje zestaw ADR-N1...N17 oraz wcześniejsze decyzje `[FREEZE]`. | Nowy dokument nie może pośrednio zmienić architektury. |
| F-003 | VERIFIED_FACT | Repozytorium stosuje Use-Case 2.0 i wymaga śledzenia nowych stories do slice albo quality/infrastructure use case. | Nowy backlog nie może zaczynać się od luźnych stories. |
| F-004 | VERIFIED_FACT | `planning/` zawiera 76 epików i capability-first dependency model. | `work/QUEUE.md` może być tylko krótką nakładką operacyjną. |
| F-005 | VERIFIED_FACT | Feature expansion jest zatrzymany do przejścia faz enterprise rebase. | Najbliższa praca powinna dotyczyć governance, traceability, architecture evidence i use-case catalogue. |
| F-006 | VERIFIED_FACT | Architektura bazuje na jednym Spring Modulith, one-writer-per-schema, selektywnym RLS, LedgerPort-only money path i jawnej finality. | Target state ma być ewolucją, nie redesignem od zera. |
| F-007 | VERIFIED_FACT | `payment-lifecycle` jest lifecycle spine; ingress, ISO, signature i egress są adapterami, a routing, settlement i ledger współpracują przez publiczne porty/zdarzenia. | Dokument granic powinien wzmacniać istniejący Context Map. |
| F-008 | VERIFIED_FACT | GraphQL jest Query-only i czyta przez source-owned ports; commands należą do REST/gRPC. | Nie projektować generic CRUD GraphQL. |
| F-009 | VERIFIED_FACT | Foundation, ingress, signature i znaczna część ISO lineage są wdrożone; settlement, routing, ledger, egress, audit i GraphQL są częściowe; reconciliation i case są głównie przyszłe. | Roadmapa powinna najpierw stabilizować istniejący credit-transfer spine. |
| F-010 | CONFLICT | `HANDOFF.md` i Wave 11 record nadal opisują brak części proofów, natomiast EPIC-26 i `planning/README.md` oznaczają Wave 11/Story 26.4 jako done z pełnymi regresjami. | Przed importem należy wykonać baseline reconciliation i odświeżyć handoff/program record. |
| F-011 | VERIFIED_FACT | Korpus zawiera 57 źródeł, 31 capabilities, 20 procesów R/claims/reporting, 15 luk oraz 23 lokalne PDF-y. | Jest wystarczający do master planning, ale nie do production readiness. |
| F-012 | VERIFIED_FACT | Korpus rozróżnia prawo, EPC, ISO, rail, bank practice, participant docs i przyszłe źródła. | Claim map musi zachowywać topic-specific authority. |
| F-013 | CONFLICT | Kontrola `SHA256SUMS-2027.txt` potwierdziła 54 z 55 pozycji; `validation_report.json` ma inny hash niż zapisany. | Korpus wymaga regeneracji checksum po wyjaśnieniu kolejności generowania raportu. |
| F-014 | VERIFIED_FACT | Manifest JSON ma 57 rekordów; wszystkie 9 rekordów oznaczonych `local_exists=yes` mają poprawne hashe. | Mismatch dotyczy integralności paczki, nie zarejestrowanych lokalnych źródeł. |
| F-015 | VERIFIED_FACT | Nowe, kluczowe dokumenty VOP/EDS/SDD nie zostały fizycznie pobrane w tym wykonaniu. | Szczegółowe kontrakty i mappingi pozostają source-blocked. |
| F-016 | VERIFIED_FACT | Dokumentacja STEP2 SDD i SATP wymaga dostępu uczestnika. | Nie wolno projektować envelope, ACK/NAK, cykli, finality i retry przez analogię. |
| F-017 | VERIFIED_FACT | Bankowe MIG-i są reprezentatywną praktyką kanałową, nie wymaganiem EPC. | Profile bankowe muszą być effective-dated i osobne dla bank/legal entity/product. |
| F-018 | VERIFIED_FACT | Finalne rulebooki/IG obowiązujące od listopada 2027 nie są jeszcze finalne w korpusie. | Rule registry musi obsługiwać future-dated draft bez aktywacji produkcyjnej. |
| F-019 | OPEN_QUESTION | Nie wykonano jeszcze rzeczywistego source-to-code audytu dla VOP, EDS i SDD. | Statusy tych capabilities muszą pozostać MISSING/SOURCE-BLOCKED do repo audit. |
| F-020 | INFERENCE | Najmniej ryzykowny kolejny krok to przyjęcie pakietu master planning, a potem pilot Use-Case 2.0 na istniejącym, dobrze udowodnionym flow. | Pozwala nauczyć się procesu bez blokowania istniejącego systemu. |

# 5. Assumptions and open questions

- `[ASSUMPTION]` Zdalny `main` odpowiada istotnej części lokalnego repo. Weryfikacja lokalnego SHA i working tree jest obowiązkowa przed importem.
- `[OPEN-QUESTION]` Czy lokalnie istnieją nowsze commity lub niecommitowane zmiany po `f601089d...`?
- `[OPEN-QUESTION]` Czy użytkownik chce modelować obowiązki banku/PSP jako pełne capabilities produktu, czy jako scenariusze badawcze/symulacyjne?
- `[OPEN-QUESTION]` Czy SDD Core jest decyzją produktową Debina, czy odległym laboratorium domenowym?
- `[OPEN-QUESTION]` Czy jest legalny i praktyczny dostęp do participant docs STEP2/TIPS/RT1/STET?
- `[OPEN-QUESTION]` Które jurysdykcje mają zostać objęte retencją, mandate validity i dispute evidence?

# 6. Main content

## 6.1 Stan governance

Repozytorium ma już konstytucję, metody admission, source authority, Use-Case 2.0, capability graph oraz semantic planning. Brakuje przede wszystkim warstwy syntezy produktu i architektury oraz wykonania faz B-C programu rebase.

## 6.2 Stan delivery

- Nie należy automatycznie kontynuować pierwszej story `in-progress`.
- Status formalny i readiness są odrębne.
- Każdy kolejny slice powinien mieć executable `verify`, właściciela, source evidence, use-case trace i brak otwartej decyzji.
- `work/QUEUE.md` nie zmienia frontmatterów epików.

## 6.3 Stan korpusu

Korpus nadaje się do:

- klasyfikacji obowiązkowości;
- budowy capability map;
- wskazania braków dowodowych;
- planowania pozyskania źródeł;
- projektowania effective-date governance.

Nie nadaje się jeszcze do:

- finalnego VOP API contract;
- EDS runtime onboarding;
- STEP2 SDD adapter contract;
- jurysdykcyjnej polityki prawnej;
- twierdzenia o readiness/certification.

# 7. Relationships to existing artifacts

- Rozwija fazy B-G `planning/programs/DEBINA-ENTERPRISE-REBASE-PROGRAM.md`.
- Nie zastępuje `planning/README.md` ani epików.
- Używa `docs/standards/SOURCE-AUTHORITY-MATRIX.md` jako polityki claims.
- Używa `docs/requirements/USE-CASE-METHOD.md` jako modelu delivery.
- Używa `docs/architecture/ARCHITECTURE-METHOD.md` jako gate dla zmian architektury.

# 8. Risks

| Ryzyko | Poziom | Mitigacja |
|---|---|---|
| Dokumenty syntetyczne stają się drugim źródłem prawdy | HIGH | Jawne `does_not_supersede`, linki do artefaktów kanonicznych, review triggers. |
| Masowa migracja 76 epików | HIGH | Pilot na małym kohorcie; brak kosmetycznego backfill. |
| Przepisanie bank MIG jako EPC | HIGH | Source classification i claim-specific references. |
| Projektowanie raila bez participant docs | HIGH | `PARTICIPANT_DOC_REQUIRED`, adapter interfaces only. |
| Rozjazd lokalnego repo z GitHub | HIGH | Dry-run import i baseline reconciliation. |
| Nadmierne rozdrobnienie modułów | MEDIUM | Aggregate/module admission record i existing-module-first. |
| Nieaktualne źródła 2027 | HIGH | freshness review i future-dated registry. |

# 9. Evidence and canonical sources

Zobacz frontmatter oraz `SOURCE-COVERAGE.md`. Repozytorium pozostaje autorytetem dla stanu projektu; korpus jest zewnętrznym źródłem dowodowym.

# 10. Review triggers

- zmiana latest commit repozytorium;
- finalna publikacja EPC 2027;
- pozyskanie VOP API YAML/EDS files/STEP2 participant docs;
- decyzja o SDD Core/B2B;
- przyjęcie lub supersession ADR wpływającego na product scope, finality, ownership lub status axes.

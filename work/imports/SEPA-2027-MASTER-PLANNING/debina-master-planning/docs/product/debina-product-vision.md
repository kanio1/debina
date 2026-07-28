---
title: "Debina Product Vision"
document_id: DMP-PRODUCT-VISION-001
status: DRAFT
document_role: PRODUCT_SYNTHESIS
baseline_repository: kanio1/debina
baseline_branch: main
baseline_commit: f601089d2f123ff01f41378f47be5dd9ce361fd2
created_at: 2026-07-27
last_reviewed_at: 2026-07-27
owner: project-owner
canonical_sources:
  - ADR-N15
  - AGENTS.md
  - planning/programs/DEBINA-ENTERPRISE-REBASE-PROGRAM.md
  - external-corpus:README.md
  - external-corpus:12_ANALYSIS/2027-scope-decision.md
  - external-corpus:12_ANALYSIS/2027-readiness-report.md
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

Zapewnić jednoznaczny, krótki opis kierunku produktu, który syntetyzuje ADR-N15 i nie zastępuje konstytucji projektu.

# 2. Scope

Debina jako syntetyczna, standards-backed platforma badawcza do projektowania, implementowania i weryfikowania nowoczesnego przetwarzania płatności SEPA/ISO 20022.

# 3. Non-goals

Debina nie jest:

- bankiem ani PSP świadczącym usługę rynkową;
- CSM, designated settlement system ani direct participant;
- certyfikowanym payment hubem;
- poradą prawną lub dowodem regulatory compliance;
- kopią STEP2, TIPS, RT1, STET ani konkretnego systemu bankowego;
- platformą microservices-first;
- projektem, którego wartość mierzy się liczbą ekranów lub testów browserowych.

# 4. Current verified facts

Projekt posiada działający modularny fundament, rzeczywiste mechanizmy bezpieczeństwa, izolowane dowody PostgreSQL/Kafka/Keycloak, payment initiation, lineage ISO, częściowy lifecycle, ledger/settlement/routing/egress i read-only operational views.

# 5. Assumptions and open questions

- `[ASSUMPTION]` Głównym użytkownikiem pozostaje jedna osoba rozwijająca kompetencje payments, architecture i SDET.
- `[OPEN-QUESTION]` Czy VOP/EDS i SDD staną się implementowanymi laboratoriami, czy wyłącznie artefaktami analitycznymi?
- `[OPEN-QUESTION]` Jaki poziom rail simulation jest wystarczający bez participant documentation?

# 6. Main content

## 6.1 Vision statement

> Debina jest ewolucyjną, modularną platformą badawczą, która uczy projektowania wiarygodnych systemów płatniczych poprzez source-backed procesy, jawne decyzje architektoniczne, wykonywalne kontrakty, deterministyczne testy i operacyjne dowody - bez udawania rzeczywistego banku lub certyfikowanego rail participant.

## 6.2 Użytkownicy

| Persona | Potrzeba | Wartość Debina |
|---|---|---|
| Payment/Solution Architect | Zrozumieć lifecycle, ownership, finality, rail boundaries | C4, ADR, use cases, state machines, traceability |
| Senior QA/SDET | Uczyć się testowania rozproszonych przepływów | Testcontainers, contract/state/mutation/runtime evidence |
| Backend/Data Engineer | Projektować modułowy system i silne granice danych | Spring Modulith, PostgreSQL constraints/RLS/grants, Kafka |
| Business Analyst/Product Owner | Przekładać standardy na mierzalne slices | Source authority, UC2, capability map, acceptance examples |
| Security/Operations Engineer | Weryfikować auth, audit, failure/recovery | Keycloak, evidence-audit, observability, smoke/runbooks |

## 6.3 Propozycja wartości

1. **Fidelity without false claims** - realizm oparty na źródłach, z jawnymi lukami.
2. **Executable learning** - każda istotna zasada prowadzi do testu lub runtime evidence.
3. **Evolutionary architecture** - jeden modularny monolit, admission zamiast spekulacyjnych modułów.
4. **Lifecycle clarity** - oddzielne statusy biznesowe, ISO, transport, receipt, settlement/finality i accounting.
5. **Evidence-first delivery** - source -> rule -> use case -> architecture -> code -> test -> evidence.

## 6.4 Product principles

- Source-backed before code.
- Business Flow/Use-Case Slice before story.
- Existing module plus public port before new module.
- Database constraints for integrity, application code for orchestration.
- One writer per schema.
- No rail-specific claim without rail-specific evidence.
- No `READY` with open decision/source/capability blocker.
- Small reversible slices and independent review.
- Documentation runway: one READY candidate, one-two DISCOVERY items.

## 6.5 Capability pillars

1. Payment initiation and canonical ingestion.
2. ISO 20022 lineage, validation and correlation.
3. Payment business lifecycle and approval.
4. Routing, settlement, ledger and finality.
5. Egress, delivery, receipts and recovery.
6. Reconciliation, cases and R-transactions.
7. Identity, evidence, audit and operational read models.
8. Scheme/rail rule governance and future effective dates.
9. SEPA 2027 discovery: Instant Payments Regulation, VOP/EDS.
10. Conditional expansion: SDD Core; optional SDD B2B/e-Mandate.

## 6.6 Success measures

- Nowa story ma UC2 trace albo quality/infrastructure UC.
- Każdy materialny claim ma source ID lub jawny gap.
- 0 nieautoryzowanych zmian frozen boundaries.
- 0 stories oznaczonych DONE bez wykonanego `verify`.
- Nowa sesja odzyskuje stan w mniej niż 5 minut.
- Każdy cross-context flow ma dynamic diagram przed implementacją.
- Każdy nowy moduł/agregat przechodzi admission.
- Implementacja nie wyprzedza documentation runway.

# 7. Relationships to existing artifacts

Dokument rozwija ADR-N15 jako czytelna synteza produktu. W razie konfliktu ADR-N15 i `AGENTS.md` wygrywają.

# 8. Risks

- rozszerzenie scope do pełnego banku bez zasobów i evidence;
- mylenie laboratoryjnego odwzorowania z compliance;
- zbyt wiele nowych modułów;
- stworzenie drugiego backlogu;
- dokumentacja przyszłych standardów stająca się nieaktualna.

# 9. Evidence and canonical sources

ADR-N15, AGENTS, rebase program, Source Authority Matrix oraz zewnętrzny scope/readiness report.

# 10. Review triggers

Superseding ADR product scope, zmiana głównego użytkownika, decyzja o SDD/VOP/rail participation, przejście do produkcyjnego use case.

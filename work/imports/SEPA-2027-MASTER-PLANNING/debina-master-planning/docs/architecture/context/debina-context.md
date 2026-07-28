---
title: "Debina C4 System Context"
document_id: DMP-C4-CONTEXT-001
status: DRAFT
document_role: ARCHITECTURE_SYNTHESIS
baseline_repository: kanio1/debina
baseline_branch: main
baseline_commit: f601089d2f123ff01f41378f47be5dd9ce361fd2
created_at: 2026-07-27
last_reviewed_at: 2026-07-27
owner: project-owner
canonical_sources:
  - docs/architecture/C4-REQUIREMENTS.md
  - docs/architecture/CONTEXT-MAP.md
  - ADR-N15
  - docs/standards/SOURCE-AUTHORITY-MATRIX.md
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

Pokazać Debinę, jej użytkowników i zewnętrzne granice bez sugerowania rzeczywistego uczestnictwa w railach.

# 2. Scope

C4 System Context dla laboratoryjnego środowiska.

# 3. Non-goals

- deployment detail;
- participant onboarding/certification;
- dokładne rail protocols;
- model prawdziwych danych klientów.

# 4. Current verified facts

Debina jest lokalną/syntetyczną platformą badawczą; integracje railowe są symulowane albo oparte na publicznych specyfikacjach.

# 5. Assumptions and open questions

- PSP/Bank to syntetyczne role/organizacje.
- Źródła standardów nie są runtime dependency, lecz authority/evidence input.

# 6. Main content

```mermaid
flowchart LR
  Architect[Payment / Solution Architect]
  Engineer[Backend / Data Engineer]
  Tester[QA / SDET]
  Operator[Synthetic Bank Operator]
  Auditor[Auditor / Evidence Reviewer]

  subgraph Trust_User[User trust boundary]
    UI[Debina Web UI and BFF]
  end

  subgraph Debina[Debina - synthetic SEPA/ISO 20022 research platform]
    Core[Payment Processing Modular Monolith]
    Data[(PostgreSQL)]
    Bus[(Kafka)]
  end

  KC[Keycloak - local identity provider]
  Sim[Rail / CSM Simulators\nPROJECT_SIMULATION]
  Standards[EU / EPC / ISO / public rail specifications\nEvidence, not runtime]
  Participant[Participant-only docs and certification\nNOT CONNECTED]
  Prod[Real bank / production rail\nOUT OF SCOPE]

  Architect --> UI
  Engineer --> UI
  Tester --> UI
  Operator --> UI
  Auditor --> UI
  UI --> Core
  Core --> Data
  Core --> Bus
  UI --> KC
  Core <--> Sim
  Standards -. source claims .-> Core
  Participant -. evidence gap .-> Standards
  Prod -. no integration .- Core
```

## Legend

- **CURRENT:** UI/BFF, Spring Modulith, PostgreSQL, Kafka, Keycloak, selected synthetic journeys.
- **SIMULATED:** rail responses and external participants.
- **EXTERNAL EVIDENCE:** law, EPC, ISO, public rail documents.
- **NOT CONNECTED:** production bank/rail and participant portals.

## Trust boundaries

1. Browser/session boundary at BFF and Keycloak.
2. Module/data ownership boundary inside Debina.
3. Local test infrastructure boundary.
4. External document/evidence boundary.
5. No production-data or participant credential boundary in this project.

# 7. Relationships to existing artifacts

Container detail: `../containers/debina-containers.md`. Domain detail: `../domain-boundaries.md`.

# 8. Risks

- diagram interpreted as production integration;
- public standards interpreted as runtime service;
- simulator behavior treated as rail truth.

# 9. Evidence and canonical sources

C4 requirements, Context Map, ADR-N15, Source Authority Matrix.

# 10. Review triggers

New external system, rail adapter admission, change to deployment/trust boundary, product scope ADR.

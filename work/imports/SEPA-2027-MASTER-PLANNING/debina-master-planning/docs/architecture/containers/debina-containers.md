---
title: "Debina C4 Containers"
document_id: DMP-C4-CONTAINERS-001
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
  - docs/architecture/current-state.md
  - AGENTS.md
  - planning/README.md
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

Pokazać główne uruchamialne kontenery i techniczne granice Debina na baseline.

# 2. Scope

UI/BFF, backend, data, messaging, identity, CI/test i observability.

# 3. Non-goals

- pełny deployment diagram;
- każdy Spring Modulith component;
- produkcyjne HA/SLA;
- future VOP/SDD containers.

# 4. Current verified facts

Aplikacja pozostaje jednym backend deployable i osobnym Next.js UI/BFF; dane są w PostgreSQL, messaging w Kafka, auth w Keycloak.

# 5. Assumptions and open questions

- Dokładny lokalny compose topology należy ponownie sprawdzić przed importem.
- Dagger/observability maturity jest częściowa/proponowana.

# 6. Main content

```mermaid
flowchart TB
  User[Users / synthetic operators]

  subgraph Web[CURRENT - Web container]
    UI[Next.js / React UI]
    BFF[Next.js BFF\nSession + fixed REST/GraphQL operations]
  end

  subgraph Backend[CURRENT/PARTIAL - Spring Modulith container]
    REST[REST command adapters]
    GQL[Query-only GraphQL adapter]
    MOD[Domain and supporting modules]
    OUTBOX[Per-schema outbox/inbox dispatch]
  end

  DB[(CURRENT - PostgreSQL 18\nmultiple owned schemas)]
  Kafka[(CURRENT - Kafka\nevents/outbox)]
  KC[CURRENT - Keycloak 26.6.4]
  Sim[SIMULATED - rail/CSM responders]
  Obs[PARTIAL - logs/metrics/traces]
  CI[CURRENT/PARTIAL - Maven/pnpm + CI\nDagger target proposed/evolving]
  Test[CURRENT - Testcontainers and synthetic runtime evidence]

  User --> UI
  UI --> BFF
  BFF --> REST
  BFF --> GQL
  REST --> MOD
  GQL --> MOD
  MOD --> DB
  MOD --> OUTBOX
  OUTBOX --> Kafka
  Kafka --> MOD
  MOD <--> Sim
  BFF --> KC
  MOD --> KC
  MOD --> Obs
  CI --> Test
  Test --> Backend
  Test --> DB
  Test --> Kafka
  Test --> KC
```

## Container responsibilities

| Container | Status | Responsibility | Does not own |
|---|---|---|---|
| Next.js UI | CURRENT/PARTIAL | Role-scoped workspaces and views | Domain rules/data |
| Next.js BFF | CURRENT | Session, fixed operations, token containment | Domain authorization decisions alone |
| Spring Modulith | CURRENT/PARTIAL | Commands, lifecycle, ports, events, persistence orchestration | External rail truth without evidence |
| PostgreSQL 18 | CURRENT | Schema-owned state, constraints, RLS/grants | Cross-schema write convenience |
| Kafka | CURRENT/PARTIAL | Asynchronous integration/outbox | Business truth/finality |
| Keycloak | CURRENT | Authentication/roles/claims | Tenant data isolation alone |
| Rail simulators | SIMULATED | Deterministic external responses | Certification fidelity |
| CI/Dagger | PARTIAL/PROPOSED | Reproducible checks | Duplicated business logic |
| Observability | PARTIAL | Operational signals | Repair/decision authority |

# 7. Relationships to existing artifacts

Context: `../context/debina-context.md`. Module boundaries: `../domain-boundaries.md`.

# 8. Risks

- one backend container mistaken for one domain module;
- Kafka status treated as business status;
- BFF becoming business layer;
- proposed Dagger/observability presented as complete.

# 9. Evidence and canonical sources

C4 requirements, planning foundation/current-state, ADR-N17, AGENTS.

# 10. Review triggers

Deployment topology change, new deployable, extraction to service, new external dependency.

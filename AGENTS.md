# AGENTS.md — Debina

Debina is a synthetic, enterprise-grade SEPA/ISO 20022 payment-processing research platform. It is not a bank, CSM, designated settlement system, certified payment hub, production integration, or regulatory-compliance claim.

## Constitutional rules

- Read `HANDOFF.md`, then `README.md`, before task-specific artifacts. Preserve uncommitted work and never use destructive Git operations. Do not push unless explicitly asked.
- ADR-N1…N17 and `[FREEZE]` decisions bind implementation. A superseding ADR is required to change one. Preserve one-writer-per-schema, RLS-only tenant isolation, LedgerPort-only money movement, explicit finality, and five separate status axes.
- Use the topic-specific authority model in [docs/standards/SOURCE-AUTHORITY-MATRIX.md](docs/standards/SOURCE-AUTHORITY-MATRIX.md). Record unknowns as `[OPEN-QUESTION]` or `[ASSUMPTION]`; do not invent rail or participant behaviour.
- New work follows [Use-Case 2.0](docs/requirements/USE-CASE-METHOD.md): future stories trace to a use-case slice or cross-cutting quality/infrastructure use case.
- Evolve the modular monolith through [the architecture method](docs/architecture/ARCHITECTURE-METHOD.md); modules/adapters, aggregates, and ADRs require their respective admission/lifecycle records.
- GraphQL is a thin, source-owned, Query-only operational-read adapter; REST/gRPC own commands. Playwright is one validation layer; follow ADR-N16 sequencing.
- Feature expansion is paused by [the rebase program](planning/programs/DEBINA-ENTERPRISE-REBASE-PROGRAM.md) until its governance phases complete.
- Business work is use-case-first: apply `enterprise-use-case-engineering` before new/material business planning, beginning with its source-discovery gate; apply `source-backed-payments-modeling` for external payment semantics, `architecture-evolution-review` for boundary changes, and `planning-semantic-integrity` before marking planning complete. New/materially changed stories use explicit `ENFORCED` metadata and are not READY without a behavioral slice and executable verify; legacy stories migrate gradually.
- Before technical planning for new or materially changed business work, obtain a `PRE_PLAN_READINESS` verdict through the existing use-case, source, architecture and planning skills. Only `READY` proceeds to technical planning.
- Changes affecting build/test commands, package manifests or lockfiles, Dagger functions, service topology, migrations exercised by composed checks, Keycloak/runtime overlays, Playwright composed execution or verification classification must declare `PIPELINE_IMPACT` and use `dagger-go-pipeline` when that impact is not `NONE`.
- Agent instruction files are executable project policy. Changes to AGENTS, Cursor rules, skills or routing metadata must pass instruction-hygiene validation and applicable routing evaluations.

## Map

| Need | Source |
|---|---|
| Product constitution and ADR index | `README.md`, ADR-N15, `docs/architecture/ADR-LIFECYCLE.md` |
| Authority, terminology, fidelity | `docs/standards/` |
| Domain/aggregate rules | `docs/domain/` |
| Use cases and traceability | `docs/requirements/` |
| Architecture, C4, quality, context map | `docs/architecture/` |
| Security and data ownership | `backend/AGENTS.md`, Keycloak and ownership blueprints |
| Planning/readiness | `planning/AGENTS.md`, `planning/README.md`, `planning/capabilities.yaml` |
| CI/Dagger | `docs/ci/DAGGER-PIPELINE-ARCHITECTURE.md`; local Dagger acceptance checks are authoritative for composed verification. Apply `dagger-go-pipeline` for Dagger changes. Remote CI and `act` remain out of scope. |
| Skills | `.claude/skills/`, `docs/governance/SKILL-ROADMAP.md` |

Read local instructions before changing `backend/`, `frontend/`, `infra/`, or `planning/`. Before ending any session, overwrite `HANDOFF.md` using the `session-handoff` skill format when the session was material (see that skill).

## Lean delivery workflow

Three lanes: **FAST** (small technical/docs/test fixes), **STANDARD** (behaviour, contracts, data, migrations), **DECISION** (costly/irreversible architecture, money path, security, new rail/product).

- Canonical backlog remains `planning/` (epics, stories, capabilities). `work/QUEUE.md` is a tiny operational overlay (`NOW`≤1, `NEXT`≤1, `LATER`≤2). `work/ACTIVE.json` is the sole machine-readable active-task state.
- STANDARD and DECISION require a human-authored approval under `work/approvals/` before implementation writes. One writer only; discovery and review use Ask/read-only.
- Never auto-commit, push, or merge. Use wrappers: `tools/agent/task-status`, `verify-fast`, `verify-task`, `final-check`.
- Explicit workflow skills (slash-invoked): `debina-discover-and-specify`, `debina-implement-and-verify`, `debina-decision-record`, `debina-review-and-next-work`. Domain procedures stay in existing `.claude/skills/` — do not copy them here.

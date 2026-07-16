# AGENTS.md — SEPA Nexus

Shared instructions for every agent runtime working in this repository (Claude Code and Codex CLI). Runtime-specific detail lives in `CLAUDE.md` (which imports this file) and in `tools/codex/README.md`; neither restates what's here.

## What this repository is

The documentation+code corpus for **SEPA Nexus** (`Playwright-Learning-Development`), a synthetic, deterministic SEPA/ISO 20022 payments platform. Its sole purpose is a realistic, enterprise-grade testing ground for a Senior QA/SDET to master Playwright, TypeScript, and enterprise test architecture — multi-role auth (Keycloak), real asynchrony (SSE, queues, deferred settlement), and UI/API/data separation (BFF + GraphQL-read-only + REST-commands + SQL-level assertions). Not a real bank, not a copy of any existing CSM/ACH/RTGS system, no regulatory-compliance claims. Any piece of SEPA "realism" that doesn't produce a new, named testing lesson is a candidate for simplification, not depth.

Current build status (what exists vs. what's still spec) is tracked in `planning/README.md`, not here — that table changes every session and this file does not.

## Kim jesteś w tym projekcie

W tym repozytorium wcielasz się jednocześnie w: **Principal Solution Architekta, Data/Integration Architekta, Security Architekta, Product Managera oraz Scrum Mastera.**

Zasada nadrzędna, obowiązująca w tej i każdej kolejnej sesji, niezależnie od runtime'u (Claude Code lub Codex CLI): **jedynym źródłem prawdy jest dokumentacja/artefakty faktycznie obecne w tym repo.** Nigdy nie wymyślaj nowej architektury. Nigdy nie zmieniaj decyzji oznaczonej jako zamrożona (ADR, `[FREEZE]`) — zmienia ją wyłącznie nowy, nadrzędny ADR. Twoja rola to **tłumaczenie tego, co już zdecydowane, na wykonalny plan pracy** (epiki/stories/taski w `/planning/`, kod zgodny z zamrożoną architekturą) — nie projektowanie od nowa. Jeśli artefakty nie odpowiadają na pytanie architektoniczne lub planistyczne, zapisz to jako otwarte pytanie (w `HANDOFF.md` lub jako `[OPEN-QUESTION]` w odpowiednim pliku epika) zamiast rozstrzygać je samodzielnie.

## Reading order / document map

Read `README.md` first — it is the ADR index and states which decisions are frozen. Then, depending on the task:

| Concern | Source of truth |
|---|---|
| Frozen architecture decisions | `README.md` + `ADR-N1`…`ADR-N8` |
| What's blocked, what's next, why (current status, all 76 epics) | `planning/README.md` |
| Domain events, schemas, DDL sketches, Kafka topic catalog (§3.7 v2) | `sepa-nexus-message-flow-and-data-blueprint.md` (large — grep for the section) |
| Module/schema ownership, dependency rules, layering, architectural tests | `sepa-nexus-blueprint-ownership-integration.md` §3.6 |
| Security/Keycloak | `sepa-nexus-keycloak-26-security-architecture-blueprint.md` |
| `signature` module design | `sepa-nexus-signature-module-blueprint.md` |
| Frontend IA, workspaces, BFF, screen inventory | `sepa-nexus-react-nextjs-frontend-blueprint.md` |
| The 9 screen specs | `sepa-nexus-first-3-screens-ui-spec.md`, `sepa-nexus-next-3-screens-ui-spec.md`, `sepa-nexus-final-3-screens-ui-spec.md` |
| React component foundation | `sepa-nexus-react-component-foundation-blueprint.md` |
| Playwright/test-learning coverage, anti-flakiness, sequencing | `sepa-nexus-playwright-test-learning-business-development.md`, `sepa-nexus-playwright-learning-vision-and-success-criteria.md` |
| Domain analysis | `sepa-nexus-domain-analysis-review.md` |
| Backlog structure, capability graph, execution waves | `planning/BACKLOG-REDESIGN.md`, `planning/capabilities.yaml` |

**A decision marked `Frozen`/`[FREEZE]` in any of these documents is binding.** It may only be changed by a new, superseding ADR — never by silently writing code or a new doc that contradicts it.

## Frozen architecture (binding — see `README.md` for the full ADR-N1..N8 table, not restated here)

- **CPC-SP topology**: one Spring Modulith deployable, one Payment Lifecycle spine, one Settlement Profile Engine keyed by `(settlement_basis, liquidity_mode)` — never by CSM/profile name — one append-only Ledger behind a triple-enforced `LedgerPort`.
- **One-writer-per-schema**: a module writes only to its own PostgreSQL schema; cross-schema writes are an architecture violation, enforced three ways (Spring Modulith `allowedDependencies`, ArchUnit, SQL grants).
- **Finality is explicit and profile-configured**: accepted/posted/delivered ≠ final. A return-after-finality is a new, opposite-direction payment, never a ledger reversal.
- **Five-status separation**: business status ≠ ISO status ≠ finality ≠ transport status ≠ receipt status — never collapse these.
- Egress owns transport state only, never finality. Reconciliation is read-only detection-and-escalation, never repair. Case is decision-and-coordination only.
- GraphQL is read-only in MVP; all writes are REST/gRPC commands.
- Controllers hold no business logic or security decisions; services own the business rule and the authorization decision together; repositories are thin and schema-scoped (tenant isolation is RLS-only — never `@TenantId`, never a Hibernate-level tenant filter). Full detail: `backend/AGENTS.md`.

Domain module map (~16 bounded-context modules, one PostgreSQL schema each): `ingress` (+`batch`), `iso-adapter` (schema `iso`), `payment-lifecycle` (schema `payment`), `signature`, `routing`, `settlement`, `ledger`, `reconciliation`, `egress`, `case`, `reference-data`, `risk`, `simulation`, `reporting`, `identity-access` (schema `security`), `evidence-audit` (schemas `evidence`, `audit`). Per-module build status: `planning/README.md`.

## Capability-first planning

Backlog work is tracked as capabilities (what a piece of work *produces* and *requires*), not just as epic numbers. When picking up work or judging a dependency:

- Prefer depending on the specific capability/story that is actually required, not the entire epic that happens to contain it, unless the whole epic genuinely gates the work.
- A story is not `READY` if it has an unresolved decision, a source conflict, a missing capability, a non-executable `verify:`, no clear owner, or a hidden cross-module design question.
- See `planning/AGENTS.md` for the full status/readiness vocabulary and `planning/capabilities.yaml` for the capability graph.

## Session protocol

1. If `HANDOFF.md` exists at repo root, read it first — it is the memory of the previous session (see skill `session-handoff`).
2. If `/planning/` does not exist, your first job is a full artifact-derived planning build (skills `artifact-derived-planning`, `epic-story-task-catalog`).
3. If `/planning/` exists, continue from the first `not-started`/`in-progress` task in dependency order, starting from `planning/README.md` as the index.
4. Before finishing the session, **always** overwrite `HANDOFF.md` per the `session-handoff` skill format — not optional, regardless of session length. `HANDOFF.md` (volatile per-session state) and this file (stable rules) are deliberately separate; never merge them.

## Git and worktree safety

- The repo may contain intentional, uncommitted changes from a previous session. Never discard them without inspecting the diff first (`git status`, `git diff`).
- Never run destructive git operations (`reset --hard`, `clean -fd[x]`, force-push) or bypass hooks/signing unless the user explicitly asks in the current turn.
- Most of this is already enforced deterministically via `.claude/settings.local.json` (`git push`/`reset --hard`/`clean` are hard-denied; `git add`/`commit`/`checkout`/`switch`/`restore` require confirmation) — treat that as the enforcement layer, this file as the reasoning layer.

## What not to do

- Do not design new modules or architecture. If artifacts don't answer a planning/architecture question, record it as an open question (`HANDOFF.md` or `[OPEN-QUESTION]` in the relevant epic file) instead of deciding it yourself.
- Do not change, "improve," or reinterpret any `Frozen`/`[FREEZE]` decision, even when it looks locally reasonable. Only a new, superseding ADR (written by the user/team) can change one.
- Playwright sequencing, per-directory build/test commands, and RLS/DB specifics: see `planning/AGENTS.md`, `backend/AGENTS.md`, `frontend/AGENTS.md`, `infra/AGENTS.md` — not restated here.

## Local instructions

| Directory | File |
|---|---|
| `backend/` | `backend/AGENTS.md` |
| `frontend/` | `frontend/AGENTS.md` |
| `infra/` | `infra/AGENTS.md` |
| `planning/` | `planning/AGENTS.md` |

Each has a paired `CLAUDE.md` that imports it (`@AGENTS.md`) plus, rarely, a short Claude-Code-only addendum.

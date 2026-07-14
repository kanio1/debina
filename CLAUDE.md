# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

This is the **analysis-phase documentation corpus for SEPA Nexus** (working title `Playwright-Learning-Development`), a synthetic, deterministic SEPA/ISO 20022 payments platform. Its sole purpose is to be a realistic, enterprise-grade testing ground for a Senior QA/SDET to master Playwright, TypeScript, and enterprise test architecture — multi-role auth (Keycloak), real asynchrony (SSE, queues, deferred settlement), and UI/API/data separation (BFF + GraphQL-read-only + REST-commands + SQL-level assertions). It is **not** a real bank, not a copy of any existing CSM/ACH/RTGS system, and makes no regulatory-compliance claims. Any piece of SEPA "realism" that doesn't produce a new, named testing lesson is a candidate for simplification, not depth.

**Current state: the first commit exists; the monorepo skeleton has no application code yet.** Most files in this repo are still Markdown design/decision documents. A `/planning/` catalog of epics/stories/tasks exists; EPIC-00 Story 0.1 created the `backend/`, `frontend/`, and `infra/` top-level directories, and Story 0.2 created `AGENTS.md` plus the project-local skills. There is still no Spring Boot/Maven build in `backend/`, no Next.js app in `frontend/`, and no docker-compose stack in `infra/` — those directories hold scaffolding only, not a working application build system. The active implementation scope remains EPIC-00; Story 0.3 is next. Continue executing `sepa-nexus-iteration-0-foundation-plan.md` task-by-task via `/planning/`.

Do not add code, scaffolding, or new design documents speculatively. If asked to start implementation, start from `sepa-nexus-iteration-0-foundation-plan.md` (see below) rather than inventing structure.

## Reading order / document map

Read `README.md` first — it is the ADR index and states which decisions are frozen. Then, depending on the task:

| Concern | Source of truth |
|---|---|
| Frozen architecture decisions | `README.md` + `ADR-N1`…`ADR-N8` |
| What's blocked, what's next, why | `sepa-nexus-decision-gate.md` |
| Domain events, schemas, DDL sketches, Kafka topic catalog (§3.7 v2) | `sepa-nexus-message-flow-and-data-blueprint.md` (large, 1300+ lines — grep for the section, don't read linearly) |
| Module/schema ownership, dependency rules, layering, architectural tests | `sepa-nexus-blueprint-ownership-integration.md` §3.6 |
| Security/Keycloak (current) | `sepa-nexus-keycloak-26-security-architecture-blueprint.md` |
| Security/Keycloak (role/claim/GUC narrative only — version specifics superseded by the doc above) | `sepa-nexus-keycloak-security-blueprint.md` |
| `signature` module design | `sepa-nexus-signature-module-blueprint.md` |
| Frontend IA, workspaces, BFF, screen inventory | `sepa-nexus-react-nextjs-frontend-blueprint.md` |
| The 9 screen specs (3 files, 3 screens each — complementary, not overlapping) | `sepa-nexus-first-3-screens-ui-spec.md`, `sepa-nexus-next-3-screens-ui-spec.md`, `sepa-nexus-final-3-screens-ui-spec.md` |
| React component foundation (shadcn/TanStack decisions) | `sepa-nexus-react-component-foundation-blueprint.md` |
| Frontend↔backend/security/data/test consistency check | `sepa-nexus-frontend-compatibility-review.md` |
| Playwright/test-learning coverage, fixtures, anti-flakiness | `sepa-nexus-playwright-test-learning-business-development.md`, `sepa-nexus-playwright-learning-vision-and-success-criteria.md` (mixed Polish/English; states the product vision) |
| **The next artifact to build against** | `sepa-nexus-iteration-0-foundation-plan.md` — checklist-driven, every task has a `verify:` command |
| Whole-corpus review that fed the decision gate | `sepa-nexus-full-blueprint-review-and-task-plan.md` |
| Domain analysis | `sepa-nexus-domain-analysis-review.md` |
| File-readiness/supersession bookkeeping (historical, not design) | `sepa-nexus-thread-files-readiness-review.md`, `sepa-nexus-prior-thread-files-analysis.md`, `sepa-nexus-epics-stories-tasks-file-readiness-gate.md`, `sepa-nexus-blueprint-ownership-integration.md`'s own change-summary — supersedes the epics/stories/tasks gate as the current readiness answer |

**A decision marked `Frozen`/`[FREEZE]` in any of these documents is binding.** It may only be changed by a new, superseding ADR — never by silently writing code or a new doc that contradicts it.

## Frozen architecture (binding on any future implementation)

Carried from the main blueprint and reaffirmed by `sepa-nexus-decision-gate.md` (never re-litigate these without a superseding ADR):

- **CPC-SP topology**: one Spring Modulith deployable, one Payment Lifecycle spine, one Settlement Profile Engine keyed by `(settlement_basis, liquidity_mode)` — **never** by CSM/profile name — one append-only Ledger behind a triple-enforced `LedgerPort`, profiles-not-engines everywhere.
- **One-writer-per-schema**: a module writes only to its own PostgreSQL schema; cross-schema writes are an architecture violation, enforced three ways (Spring Modulith `allowedDependencies`, ArchUnit, SQL grants).
- **Finality is explicit and profile-configured**: accepted/posted/delivered ≠ final. A return-after-finality is a **new, opposite-direction payment**, never a ledger reversal.
- **Five-status separation**: business status ≠ ISO status ≠ finality ≠ transport status ≠ receipt status — never collapse these.
- Egress owns transport state only, never finality. Reconciliation is read-only detection-and-escalation, never repair. Case is decision-and-coordination only, never a workflow engine.
- GraphQL is **read-only** in MVP; all writes are REST/gRPC commands.
- Simulation exercises only public paths — its sole entry point into real traffic is the `csm.response.received` topic.
- PostgreSQL 18 is the stable baseline; PostgreSQL 19 is lab/experimental only, never in the MVP plan.
- Maven, not Gradle. Selective RLS (tenant/evidence tables: yes; queue/ledger tables: ownership grants, not RLS). Raw evidence archive never deduplicates. FSM, not a BPMN/DMN workflow engine.
- Controllers hold no business logic or security decisions; services own the business rule **and** the authorization decision together; repositories are thin and schema-scoped (tenant isolation is RLS-only — never `@TenantId`, never a Hibernate-level tenant filter).

The eight new ADRs (`ADR-N1`–`ADR-N8`) close specific contradictions found in the earlier drafts:

| ADR | Decision |
|---|---|
| N1 | Iteration 0 (platform skeleton) is a mandatory phase preceding Iteration 1; zero business flow ships in Iteration 0. |
| N2 | `routing` is an in-process Modulith module for MVP (consumes `payment.validated`, no gRPC call); out-of-process gRPC extraction is a deferred P2 educational exercise. |
| N3 | Next.js BFF token model: server-side session (HttpOnly cookie), browser never holds a Keycloak access/refresh token. DPoP-SPA is a documented future alternative, not the default. |
| N4 | SSE (not GraphQL subscriptions) for MVP live feeds, proxied server-side through the BFF; `live-analytics` folds into `reporting`. |
| N5 | Every module schema owns its own `<schema>.outbox_events`/`<schema>.inbox_events` (no global unowned table); one shared-kernel `outbox_dispatcher_role` claims/marks-published across all of them with no domain-table grants. |
| N6 | One priority taxonomy: `[MVP]` = Iterations 0–5, `[P1]` = wave 1, `[P2]` = wave 2/labs — iteration number is primary, the tag is secondary. `simulation` is `[MVP]` Iteration 3 (not deferrable — it's the demo that proves determinism). |
| N7 | `JSON_DIRECT` is a seeded, synthetic ISO message-type/version so JSON-submitted payments still get identifiers + lineage recorded through the same `iso.iso_messages`/`iso.payment_iso_identifiers` path as XML — no nullable `iso_message_id` shortcut. |
| N8 | The main blueprint's §3.7 v2 Kafka topic table is the sole AsyncAPI source; no patch document restates or renames a topic. |

## Domain module map (target architecture, not yet built)

CPC-SP defines ~16 bounded-context modules, each owning exactly one PostgreSQL schema (`sepa-nexus-blueprint-ownership-integration.md` §3.6.2/§3.6.3 is authoritative): `ingress` (+`batch`), `iso-adapter` (schema `iso`), `payment-lifecycle` (schema `payment`), `signature`, `routing`, `settlement`, `ledger`, `reconciliation`, `egress`, `case`, `reference-data`, `risk` (absorbs `vop`), `simulation`, `reporting` (absorbs `live-analytics`), `identity-access` (schema `security`), `evidence-audit` (schemas `evidence`, `audit`).

Money only ever moves through `ledger` via `LedgerPort` — `settlement` never writes `ledger.journal_*` directly. `signature` verification runs **before** ISO XML parsing (verify-before-parse is an enforced ordering rule, not just documentation), and `signature` never writes to any other module's schema. Full allowed/forbidden dependency matrix: ownership blueprint §3.6.4.

## Planned tech stack (per `sepa-nexus-iteration-0-foundation-plan.md`)

Not yet installed anywhere in this repo — recorded here so implementation doesn't have to re-derive it, and re-verify versions if starting significantly later than this plan's date:

- Backend: JDK 25, Spring Boot 4.1.x (Spring Framework 7.0.x), Spring Modulith 2.x, Spring Kafka 4.1.x, Maven via `./mvnw` (not Gradle).
- Data/infra: PostgreSQL 18, Keycloak 26.6.4 (pin exact patch), Kafka in KRaft mode — all via `infra/docker-compose.yml`.
- Frontend: Node.js 24 LTS (exact pin `24.18.0`), Next.js 16.2.10+ (security-pinned — earlier 16.2.x has an unpatched middleware/proxy auth-bypass advisory the BFF security model depends on), React 19, TypeScript 7.0 (fall back to latest 5.x LTS if `typescript-eslint` lags 7.0 GA), shadcn/ui CLI v4 (Base UI, vendored components — never an npm dependency), Tailwind v4, TanStack Table.
- Testing: JUnit 5 + Testcontainers for backend/integration; Playwright is explicitly **out of scope until Iteration 1+**, once real screens exist — do not scaffold Playwright during Iteration 0.

Planned monorepo layout: `backend/` (Maven/Spring), `frontend/` (Next.js), `infra/` (compose, Keycloak realm export, Flyway reference). Iteration 0 also created root `AGENTS.md` and five project-local Claude Code / Codex CLI skills under `.claude/skills/`. Because root `.agents/` is a system read-only mount in this environment, Codex uses the supported fallback `tools/codex/.agents/skills/` via `codex --cd tools/codex`: `spring-modulith-module`, `postgres-rls-migration`, `keycloak-realm-config`, `nextjs-bff-route`, `shadcn-component-scaffold` — their exact required content is specified in Story 0.2 of the foundation plan.

## Working conventions in this pre-code phase

- These documents use bracketed tags as load-bearing status markers, not decoration: `[FREEZE]`/`Frozen` (binding, needs a superseding ADR to change), `[MVP]`/`[P1]`/`[P2]` (priority per ADR-N6), `[ADD]`/`[CHANGE]`/`[DEFER]`/`[REJECT]` (what a patch pass did to a prior gap), `[NO-CODE]` (analysis-only document, nothing to implement from it directly).
- When `sepa-nexus-iteration-0-foundation-plan.md` becomes the active work: every task ends in a `verify:` command. Do not mark a task done, or move to the next one, until that exact command passes — do not batch unverified tasks.
- Cross-project note: several general-purpose Claude Code skills already configured for this user (`payment-quality-lab-orchestrator`, `spring-modulith-2-0-6-modular-monolith-testing`, `spring-boot4-spring7-backend-architect`, `postgres18-data-architecture-and-risk`, `rest-api-security-oauth-testing`, `typescript6-playwright-engineering`, `junit6-assertj-restassured-testcraft`, `parallel-test-architecture-and-data-isolation`, `spec-kit-feature-workflow`, `business-analysis-and-product-discovery-for-payment-lab`) map directly onto this project's domains and versions. Prefer invoking the matching skill over improvising when a task falls in its territory.

## Kim jesteś w tym projekcie

W tym repozytorium wcielasz się jednocześnie w: **Principal Solution Architekta, Data/Integration Architekta, Security Architekta, Product Managera oraz Scrum Mastera.**

Zasada nadrzędna, obowiązująca w tej i każdej kolejnej sesji: **jedynym źródłem prawdy jest dokumentacja/artefakty faktycznie obecne w tym repo.** Nigdy nie wymyślaj nowej architektury. Nigdy nie zmieniaj decyzji oznaczonej jako zamrożona (ADR, `[FREEZE]`) — zmienia ją wyłącznie nowy, nadrzędny ADR. Twoja rola to **tłumaczenie tego, co już zdecydowane, na wykonalny plan pracy** (epiki/stories/taski w `/planning/`, kod zgodny z zamrożoną architekturą) — nie projektowanie od nowa. Jeśli artefakty nie odpowiadają na pytanie architektoniczne lub planistyczne, zapisz to jako otwarte pytanie (w `HANDOFF.md` lub jako `[OPEN-QUESTION]` w odpowiednim pliku epika) zamiast rozstrzygać je samodzielnie.

## Na początku każdej sesji, zanim zrobisz cokolwiek innego

1. Jeśli w korzeniu repo istnieje `HANDOFF.md` — przeczytaj go najpierw. To Twoja pamięć z poprzedniej sesji (zob. skill `session-handoff`).
2. Jeśli katalog `/planning/` **nie istnieje** — Twoim pierwszym zadaniem w tej sesji jest przeprowadzić pełną analizę artefaktów projektu i zbudować go od zera (zob. skille `artifact-derived-planning` i `epic-story-task-catalog`).
3. Jeśli `/planning/` **już istnieje** — kontynuuj od pierwszego zadania w statusie `not-started` lub `in-progress`, w kolejności zależności zapisanej w polu `depends_on` plików epików (`/planning/epics/EPIC-NN-*.md`), zaczynając od `/planning/README.md` jako indeksu.

## Zanim zakończysz sesję

Zawsze napisz/nadpisz `HANDOFF.md` w korzeniu repo, zgodnie z formatem opisanym w skillu `session-handoff`. To nie jest opcjonalne, niezależnie jak krótka była sesja.

`HANDOFF.md` i `CLAUDE.md` to dwa celowo oddzielne pliki o różnych rolach: `CLAUDE.md` to stałe zasady projektu (ten plik, zmieniany rzadko), `HANDOFF.md` to zmienny stan bieżącej pracy, w całości nadpisywany co sesję. Nigdy ich nie łącz.

## Czego nie robić

- Nie pisz, nie konfiguruj i nie scaffolduj testów Playwright, dopóki dokumentacja projektu (dokument wizji/pokrycia Playwright — `sepa-nexus-playwright-learning-vision-and-success-criteria.md`, `sepa-nexus-playwright-test-learning-business-development.md`) nie wskaże, że ten moment sekwencjonowania nadszedł. Iteracja 0 ma jawną regułę `[NO-PLAYWRIGHT]`.
- Nie projektuj nowych modułów ani architektury. Jeśli artefakty nie odpowiadają na pytanie planistyczne — zapisz to jako otwarte pytanie w `HANDOFF.md` (lub w pliku epika) zamiast rozstrzygać je samodzielnie.
- Nie zmieniaj, nie "ulepszaj" ani nie reinterpretuj żadnej decyzji oznaczonej `Frozen`/`[FREEZE]` — także wtedy, gdy wydaje się to lokalnie rozsądne. Zmiana wymaga nowego, nadrzędnego ADR napisanego przez użytkownika/zespół, nie przez Ciebie w locie.

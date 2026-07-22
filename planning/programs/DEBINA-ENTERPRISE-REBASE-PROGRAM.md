# Debina Enterprise Rebase Program

Feature expansion remains paused through Phase F evaluation of Wave 12.

| Phase | Inputs | Outputs | Non-goals | Completion / branch |
|---|---|---|---|---|
| C1 — BA/use-case skills and semantic validators | Phase B catalogues, maker-checker | enterprise-use-case-engineering, source-backed-payments-modeling, planning-semantic-integrity, architecture-evolution-review skills; five validators | UC mass migration, features | complete on `rebase/enterprise-evolution`; record: `DEBINA-ENTERPRISE-REBASE-PHASE-C1.md` |
| C2 — enterprise use-case catalogue | C1 | source-backed Cockburn/UC2 catalogue, concept admission, dynamic flows, dependency/sequence and bounded backlog maps | production changes | complete on `rebase/enterprise-evolution`; record: `DEBINA-ENTERPRISE-REBASE-PHASE-C2.md` |
| Methodology assurance — inserted review | Phase B/C1/C2 at `518345a` | source-backed method audit, adaptive profiles, honest review states, representative remediation and adversarial validators | Dagger, production changes | complete on actual `rebase/enterprise-evolution`; record: `DEBINA-ENTERPRISE-USE-CASE-METHODOLOGY-ASSURANCE.md` |
| D — local Dagger Go SDK | C1/C2 plus methodology assurance and supported commands | local fast/integration/smoke/all | GitHub Actions, act, remote CI, deployment/release | direct local invocation works; continue on `rebase/enterprise-evolution` |
| E — controlled backlog migration | C1 validators + C2 records | traced cohorts: completed, in-progress, READY, MVP, P1, P2 | 304-story bulk rewrite | each cohort validates; continue on `rebase/enterprise-evolution` |
| F — evaluate Wave 12 WIP | all above + `wip/wave-12-signature-verdict-evidence` at `5ebebb0` | evidence/decision packet | switching, merging, copying or resuming Wave 12 before review | accepted evaluation; continue on `rebase/enterprise-evolution`; Wave 12 remains isolated |
| G — resume enterprise features | approved UC2 slice + readiness | use-case-slice delivery | broad backlog implementation | executable verify/evidence; per-slice branch |

Remote CI provider selection is `DEFERRED` throughout. Minimal Playwright smoke is Phase D scope only; full acceptance remains deferred.

## Phase D ADR-N16 smoke exception (approved 2026-07-22)

ADR-N16 and Phase D authorize one narrow local exception to EPIC-24's general Playwright sequencing: a minimal Chromium-only Dagger smoke foundation and only the six named ADR-N16 journeys. This exception does not change EPIC-24 story status, does not unblock the Operations Control Room, reporting/SSE, general Playwright acceptance, axe-core, visual/cross-browser testing, remote CI or runtime compose. Phase D must use direct ephemeral Dagger services and may not invent missing product behavior.

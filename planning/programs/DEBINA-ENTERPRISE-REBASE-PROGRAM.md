# Debina Enterprise Rebase Program

Feature expansion remains paused through Phase F evaluation of Wave 12.

| Phase | Inputs | Outputs | Non-goals | Completion / branch |
|---|---|---|---|---|
| C1 — BA/use-case skills and semantic validators | Phase B catalogues, maker-checker | enterprise-use-case-engineering, source-backed-payments-modeling, planning-semantic-integrity, architecture-evolution-review skills; five validators | UC mass migration, features | pilot maker-checker works; `rebase/phase-c1-ba-semantic-governance` |
| C2 — pilot/top enterprise use cases | C1 | UC2 specs/slices/rules/examples/qualities/realizations for maker-checker, signed pain.001, single SCT, file SCT, SCT Inst | production changes | reviewed source-backed records; `rebase/phase-c2-enterprise-use-cases` |
| D — local Dagger Go SDK | C1/C2 and supported commands | local fast/integration/smoke/all | GitHub Actions, act, remote CI, deployment/release | direct local invocation works; `rebase/phase-d-local-dagger` |
| E — controlled backlog migration | C1 validators + C2 records | traced cohorts: completed, in-progress, READY, MVP, P1, P2 | 304-story bulk rewrite | each cohort validates; `rebase/phase-e-backlog-migration` |
| F — evaluate Wave 12 WIP | all above + `wip/wave-12-signature-verdict-evidence` at `5ebebb0` | evidence/decision packet | switching, merging, copying or resuming Wave 12 before review | accepted evaluation; `rebase/phase-f-wave12-evaluation` |
| G — resume enterprise features | approved UC2 slice + readiness | use-case-slice delivery | broad backlog implementation | executable verify/evidence; per-slice branch |

Remote CI provider selection is `DEFERRED` throughout. Minimal Playwright smoke is Phase D scope only; full acceptance remains deferred.

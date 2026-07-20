# Wave 1 independent review

Review perspective: routing ambiguity, stale assumptions, external acquisition security, ADR/source precedence, duplication, progressive disclosure, completion honesty, licenses, hidden behavior and maintainability.

| Finding | Resolution |
| --- | --- |
| Existing Keycloak skill treated four historical roles as permanent authority. | Removed fixed count; require current role → capability → API → screen → test source inspection. |
| Existing UI skill used npm and a blanket no-Playwright assumption. | Require project package-manager inspection (`pnpm`) and dynamic EPIC-24/capability classification. |
| Existing Modulith skill required events for every cross-module interaction. | Added interaction classification, narrow ports and frozen outbox boundary protection. |
| Existing planning wording did not explicitly order decision evidence/readiness or goal checkpoints. | Added source precedence, readiness taxonomy and evidence-backed continuation rules. |
| Cross-role command security could be confused with ordinary RLS migration work. | Added explicit boundary and reciprocal handoff between transaction and RLS skills. |
| External repositories can contain unsafe instructions/scripts. | Cloned only four approved sources in `/tmp`; read text only; executed nothing from them; no script copied. |
| Trail of Bits license is CC-BY-SA-4.0. | Used as review inspiration only; all prose original and notices/lock record that no wording was copied. |
| Static overlap checking could be misrepresented as model-routing proof. | Validator says heuristic only; behavior run is explicitly `NOT_EXECUTED`. |
| Requested `.agents/skills` canonical destination cannot be written in this checkout. | Unresolved environmental blocker documented: `.agents` is read-only. Effective tracked root remains `.claude/skills`; no fake symlink, duplicate, or user-home coupling was created. |

No production Java, TypeScript, SQL migration, runtime configuration, payment behavior, or frozen architecture decision was changed. No hidden network action is embedded in a skill. The independent reviewer found no remaining fixable Wave 1 issue; canonical-path migration requires a writable repository path supplied by the owner/environment.

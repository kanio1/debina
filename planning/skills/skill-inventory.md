# Repository-local skill inventory

Inventory date: 2026-07-20. The tracked effective root is `.claude/skills`; `.agents` exists but is a read-only system mount (`mkdir .agents/skills` → `Read-only file system`). User-level directories were read-only inspected and were not modified. No repository-local duplicate frontmatter names were found before Wave 1.

| Skill | Original path | Tracked | Trigger / state | References/scripts | Assessment |
| --- | --- | --- | --- | --- | --- |
| artifact-derived-planning | `.claude/skills/artifact-derived-planning` | yes | planning artifacts | none | useful; Polish trigger/stale readiness behavior hardened |
| postgres-rls-migration | `.claude/skills/postgres-rls-migration` | yes | RLS/Flyway/GUC | 5 refs | useful; roles, proof and function handoff hardened |
| spring-modulith-module | `.claude/skills/spring-modulith-module` | yes | module changes | none | useful; previous event-only rule stale; API/transaction rules hardened |
| keycloak-realm-config | `.claude/skills/keycloak-realm-config` | yes | realm/client/role | none | useful; fixed-four-role assumption stale and removed |
| shadcn-component-scaffold | `.claude/skills/shadcn-component-scaffold` | yes | components/screens | none | useful; npm and blanket Playwright assumptions stale and removed |
| epic-story-task-catalog | `.claude/skills/epic-story-task-catalog` | yes | planning format | none | preserved; distinct formatting scope |
| session-handoff | `.claude/skills/session-handoff` | yes | session memory | none | preserved; distinct lifecycle scope |
| nextjs-bff-route | `.claude/skills/nextjs-bff-route` | yes | BFF security | none | preserved; distinct server/BFF scope |
| sepa-nexus-database-review | `.claude/skills/sepa-nexus-database-review` | yes | post-change DB review | 4 refs | preserved; independent review scope |
| sepa-nexus-database-testing | `.claude/skills/sepa-nexus-database-testing` | yes | DB/Kafka tests | 8 refs | preserved; integration-test scope |
| sepa-nexus-flyway-safe-change | `.claude/skills/sepa-nexus-flyway-safe-change` | yes | safe schema change | 6 refs | preserved; general migration scope |
| sepa-nexus-payments-data-integrity | `.claude/skills/sepa-nexus-payments-data-integrity` | yes | identifiers/idempotency/evidence | 7 refs | preserved; broader integrity scope |
| impeccable | `.claude/skills/impeccable` | yes | frontend design/audit | many refs/scripts | preserved existing skill; outside Wave 1 authoring scope |
| debina-postgres-transaction-coordination | new `.claude/skills/...` | yes | approved cross-role one-tx | 4 refs | created; no production implementation |
| debina-money-movement-invariants | new `.claude/skills/...` | yes | ledger/reservation | 3 refs | created; distinct from finality |
| debina-payment-state-finality | new `.claude/skills/...` | yes | five status axes/finality | 3 refs | created; distinct from accounting |
| debina-runtime-proof-testing | new `.claude/skills/...` | yes | runtime completion evidence | 4 refs | created; distinct from planning readiness |

The only discovered non-source `SKILL.md` files were under `frontend/node_modules`; they are dependency content, not repository-local active skills. No legacy copy was moved because the mandated destination cannot be written; leaving `.claude/skills` intact avoids duplicate active names and preserves existing references.

# Skill Roadmap

| Skill | Trigger / inputs | Workflow and mandatory artifacts | Boundaries, validation, anti-patterns |
|---|---|---|---|
| source-backed-payments-modeling | payment concept, rule, rail, ISO message | inspect registry/authority; produce concept/rule/admission record | no participant invention; validate tags, versions, assumptions |
| enterprise-use-case-engineering | capability/use case/story | derive UC2 use case, slices, rules, quality scenarios, trace | no story without slice/quality UC; validate trace links |
| architecture-evolution-review | module/ADR/boundary change | C4/context/ATAM-lite and admission evidence | no frozen-boundary change; validate catalog/dependency/security effects |
| planning-semantic-integrity | planning status/capability update | reconcile inventory, code evidence and readiness | no cosmetic status change; run semantic validators |
| dagger-go-pipeline | CI/pipeline work | define Go Dagger check, thin trigger mapping, evidence | no duplicated Actions logic; validate target parity |
| demand-driven-graphql-read-model | proposed operational read | prove journey→source port→schema→resolver→codegen→BFF | no CRUD/federation/table schema; structural/security/perf validation |

These are designs, not installed skills. Implement only when the follow-up governance goal accepts conventions and validator integration.

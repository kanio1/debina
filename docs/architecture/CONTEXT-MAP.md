# Context Map

`payment-lifecycle` is the lifecycle spine. `ingress`, `iso-adapter`, `signature`, and `egress` are adapters around it; `routing`, `settlement`, and `ledger` collaborate only through public ports/events, with LedgerPort the exclusive money path. `reference-data` supplies configuration; `evidence-audit` supplies append-only evidence; `security` supplies the implemented identity/access boundary. `graphql` and Next.js BFF are technical adapters, not domain bounded contexts: they read source-owned ports and never own domain data.

One writer owns each schema; integration never grants a consumer direct foreign-schema write access. Finality remains settlement-owned and explicit; egress owns transport state only; reconciliation detects/escalates and does not repair. The detailed relationship contract is [CONTEXT-RELATIONSHIPS.yaml](CONTEXT-RELATIONSHIPS.yaml).

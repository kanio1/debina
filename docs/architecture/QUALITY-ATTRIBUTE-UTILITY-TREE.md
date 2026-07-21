# Quality Attribute Utility Tree

| Utility | Quality | Scenario focus |
|---|---|---|
| Correct payment research | fidelity | source-backed terminology/rules and explicit assumptions |
| Safe money semantics | integrity | LedgerPort, immutable evidence, explicit finality |
| Tenant safety | security | RLS, least privilege, BFF session boundary |
| Recoverable processing | reliability | idempotency, replay, rollback, observable failure |
| Evolvable monolith | modifiability | source-owned ports, module boundaries, adapter isolation |
| Operational learning | observability | correlated evidence, health, controlled smoke |
| Efficient verification | testability | deterministic Testcontainers and demand-driven UI/API tests |

## Prioritized leaves

| Priority | Quality attribute | Utility focus |
|---|---|---|
| High | functional correctness, transactional integrity, idempotency, tenant isolation, authorization | trustworthy payment research |
| High | auditability, traceability, source traceability, recoverability, failure isolation | evidence-backed operation |
| Medium | interoperability, modifiability, extensibility, testability, deployment repeatability | source/rail evolution and local verification |
| Medium | availability, event ordering, observability, operability, privacy, retention | controlled lab operation |
| Guardrail | performance, scalability, cognitive load, documentation integrity | PROJECT-TARGET or OPEN-QUESTION until measured |

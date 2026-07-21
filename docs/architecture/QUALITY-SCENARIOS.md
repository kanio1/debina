# Quality Scenarios

| ID | Stimulus / response measure | Realization |
|---|---|---|
| QS-SEC-01 | Cross-tenant read/write attempt is denied with no leaked row | PostgreSQL RLS and role/grant tests |
| QS-INT-01 | Replayed payment command produces one durable business effect | idempotency keys, append-only evidence, runtime proof |
| QS-REL-01 | Failure inside a coordinated command rolls back all owned effects | transaction-bound ports/functions and Testcontainers proof |
| QS-MOD-01 | Adapter cannot access source repository/foreign schema | Modulith/ArchUnit structural tests |
| QS-OPS-01 | Operator can correlate a submitted payment to source-owned evidence | read ports, allowlisted GraphQL/BFF operations |

# Debina — final Dagger pipeline architecture implementation contract

## Outcome

The local Dagger Go SDK verification platform is complete only when the public
canonical surface is:

```text
fast
integration
smoke-auth
smoke-payments
smoke-suite
acceptance
pipeline-assurance
backend-testcontainers
full-local
```

`acceptance` is the sole automatic `+check`. It runs `fast` and `integration`
in parallel, then runs `smoke-suite`. `pipeline-assurance` remains an
independent public gate and is not part of acceptance. `full-local` runs
`acceptance` and then the complete `backend-testcontainers` classification
exactly once through an explicitly supplied typed Podman socket.

Deprecated compatibility aliases may remain callable, but must never appear in
canonical compositors or become automatic checks. No Dagger function may invoke
a nested Dagger CLI.

## Test classification

Every discovered backend JUnit test class must belong to exactly one durable
classification:

- `fast`: no Testcontainers runtime dependency;
- `testcontainers`: requires the explicitly supplied OCI runtime socket.

The classification must be selected through JUnit/Maven tags, not a
hand-maintained class list. A regression must fail on missing or conflicting
tags. The union of both classifications must be coverage-equivalent to the
complete backend suite, and their intersection must be empty.

## Canonical graphs

```text
integration
├── backend-integration
├── frontend-production-build
├── database-contract
├── database-upgrade
└── kafka-contract

database-contract
└── one fresh PostgreSQL service
    ├── readiness
    ├── migrate + validate
    ├── credential contract
    └── RLS + grants

acceptance
├── parallel: fast + integration
└── sequential: smoke-suite

full-local
├── acceptance
└── backend-testcontainers
```

`database-upgrade` owns its own independent database. Browser smoke remains
strictly sequential. Frontend production-build reuse must follow demonstrated
build-time/runtime inputs without changing runtime behavior.

## Waves and terminal conditions

| Wave | Required outcome |
|---|---|
| 0 | Reconstruct HEAD/status/HANDOFF/runtime evidence; prove toolchain and record this contract. |
| 1 | Canonical names/topology: `Acceptance`, `SmokeSuite`, public `PipelineAssurance`, honest cache-leaf names and deprecated aliases; AST regression. |
| 2 | Durable JUnit `fast`/`testcontainers` tags, classification regression, Maven counts and coverage equivalence; `BackendTestcontainers` and `BackendRegressionAll`. |
| 3 | Final five-leaf integration graph and single-database `database-contract`; separate upgrade proof. |
| 4 | Prove frontend build-time/runtime inputs and maximize safe build/dependency/source reuse without runtime change. |
| 5 | Final orchestration plus complete `tools/ci/verify-dagger-architecture.sh` and all focused assurance runners. |
| 6 | Full static/runtime matrix, aliases, coverage equivalence, cache/log/generator/lock/cache-volume/unexpected-failure and protected-file audit. |
| 7 | Align manifest, blueprint, implementation record, migration table, exact results, commits and HANDOFF; leave a clean worktree. |

Every wave requires a focused proof, a complete five-section `HANDOFF.md` and a
logical local commit. No push is permitted.

The only successful terminal marker is
`DAGGER-ACCEPTANCE-ARCHITECTURE-COMPLETE`. A genuine environment or authority
blocker is recorded as `DAGGER-ACCEPTANCE-ARCHITECTURE-BLOCKED`; a failing proof
is work to fix, not a completion condition.

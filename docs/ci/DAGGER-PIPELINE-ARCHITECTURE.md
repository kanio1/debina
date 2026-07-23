# Dagger Pipeline Architecture

## Status and scope

Phase D remains local-only on Dagger CLI/Engine `v0.21.4`, rootful Podman and the
approved Engine PID limit of `16384`. Remote CI, `act`, Dagger Cloud, deployment,
publishing and Phase E remain deferred by the rebase program and ADR-N16.

The Go SDK module at `dagger.json` orchestrates repository-authoritative commands.
It never starts a nested Dagger CLI process. Stateful services are lazy, bound by
explicit aliases, have no host ports, and are isolated per journey.

## Check topology

`// +check` means automatic discovery by unfiltered `dagger check`; a public Go
function without that directive remains callable with `dagger call`.

```text
dagger check
└── acceptance                      # the only +check
    ├── fast
    ├── integration
    └── smoke-suite                 # sequential, complete ADR-N16 cap
        ├── smoke-auth              # D3A
        └── smoke-payments          # sequential D3B leaves
            ├── json-direct-submission
            ├── maker-checker-approval
            └── payment-detail-lineage

dagger call pipeline-assurance      # independent failure/cache/DNS proofs
```

Before the architecture review, `fast`, `integration`, `smoke`, `smoke-auth`,
`smoke-payments`, `phase-d` and `all` were all automatic checks while aggregates
called their children. An unfiltered check therefore described `fast ×3`,
`integration ×3`, D3A `×4`, each D3B journey `×3`, and assurance `×2`.
A disposable light compositor reproduced concurrent roots and three executions
of an analogous leaf. `dagger/pure/check_topology_test.go` now rejects any
automatic check except `Acceptance`. The pure compositor also rejects empty
names, nil runners and duplicate names before executing a graph.

## Public function contracts

| Function | Contract |
|---|---|
| `fast` | governance, module compile/tests/vet/gofmt, backend architecture checks, frontend GraphQL drift/lint/typecheck |
| `integration` | socket-free backend subset, frontend build, PostgreSQL/Flyway/RLS/grants and Kafka |
| `smoke-suite` | all six capped ADR-N16 journeys, sequential and isolated |
| `smoke` | deprecated callable alias of `smoke-suite` |
| `smoke-auth` | separately callable D3A login/session/health compatibility gate |
| `smoke-payments` | three isolated D3B journeys, strictly sequential |
| `pipeline-assurance` | verification-platform failure, timeout, redaction, cache and DNS proofs; no product tests |
| `acceptance` | exactly fast + integration + smoke-suite; complete socket-free graph and the only automatic check |
| `phase-d` | backward-compatible callable alias of `acceptance`; never automatic |
| `all-socket-free` | unambiguous callable alias of `acceptance` |
| `all` | legacy callable alias of `all-socket-free`; it is not literal host-socket verification |
| `backend-testcontainers --runtime-socket=...` | exactly the durable JUnit `testcontainers` classification |
| `backend-regression-all --runtime-socket=...` | unfiltered coverage-equivalence oracle; not a compositor child |
| `full-local --runtime-socket=...` | `acceptance`, then `backend-testcontainers`; each backend classification exactly once |

The three acceptance classifications are intentionally disjoint: `fast` owns
static/unit/architecture feedback, `integration` owns non-browser component and
service interaction, and `smoke-suite` owns browser journeys.
`pipeline-assurance` owns tests of the pipeline itself and is deliberately
independent of acceptance. Diagnostic sub-gates remain public without becoming
additional automatic roots.

This is Model A. The typed host socket is required only by `full-local` and the
three explicit Testcontainers functions. It is mounted only at
`/var/run/docker.sock` inside the dedicated Maven container.

## Workspace and invalidation boundaries

The auto-injected `*dagger.Workspace` is captured by the module constructor and
its filtered root directory is serialized in module state. Calling
`dag.CurrentWorkspace()` only inside cacheable functions had allowed a dirty
tracked source change to reuse an older function result; a controlled frontend
digest proof reproduced that failure and proved the constructor correction.

| Boundary | Included inputs |
|---|---|
| backend | Maven wrapper, `.mvn`, `backend/`, test-owned AsyncAPI catalog and canonical Keycloak realm export |
| frontend | `frontend/` plus the source-owned backend GraphQL schema |
| Dagger | `dagger/` |
| governance | filtered repository root |
| Keycloak | canonical realm file plus the Dagger overlay helper |

Global exclusions remove `.git`, build outputs, dependency directories, reports,
coverage and runtime logs. Frontend dependency installation is constructed from
only `package.json`, `pnpm-lock.yaml` and `pnpm-workspace.yaml`; source/spec
changes reuse that layer, while a lockfile change invalidates it. Generated
GraphQL source remains included and is compared after codegen.

## Images, lockfile and updates

All ten `Container.From` addresses are declared once in `dagger/main.go` and
covered by an AST regression. Root `.dagger/lock` is the reviewed digest record.

- Developer default remains normal Dagger lookup behaviour.
- Reproducibility and acceptance proofs use `--lock=frozen`.
- Maintainers run a deliberate `--lock=live` image probe to create/update entries,
  review address and digest changes, then commit only `.dagger/lock`.
- A missing frozen entry must fail closed; this was proved by removing and
  restoring one entry with controlled patches.
- Reverting the lockfile commit restores the previous image digests.
- Engine/CLI updates and base-image lock updates are separate changes and proofs.

## Cache policy and proof

Maven (`debina-maven-jdk25`), pnpm
(`debina-pnpm-node24.18.0-pnpm10.33.0`) and Go
(`debina-dagger-go-1.26.5`) caches are dependency-only, toolchain-versioned and
explicitly `SHARED`. The topology avoids duplicate gate writers; Maven and pnpm
retain their own concurrent-store protections. `PRIVATE` is rejected because it
can split lazy service identity; blanket `LOCKED` is rejected without evidence
that serialization is needed.

`tools/ci/verify-dagger-cache.sh` creates a unique cold probe and records
`cold.log`, `warm.log`, `changed-input.log`, relevant vertex IDs and output
digests. It separately proves:

- `OUTPUT-DETERMINISM-PROVEN`;
- `CACHE-REUSE-TRACE-PROVEN` from an explicit function `CACHED` span;
- `SELECTIVE-INVALIDATION-TRACE-PROVEN` from an executed changed-input vertex
  with independent file layers cached.

The module never claims cache reuse from equal digests alone. An isolated
hash-namespaced `cache-volume-stress` proof performs two concurrent writers,
reads both results, and can reset only its own fixture files.

## Services, secrets and log health

PostgreSQL, Kafka, Keycloak PostgreSQL, Keycloak, backend and frontend remain
lazy services with explicit aliases, finite readiness clients and graph-local
identity. `service-binding-dns` verifies `/etc/hosts`, DNS resolution and HTTP
reachability after `WithServiceBinding` plus a subsequent `WithExec`; it passes
on 0.21.4 and the evaluated 0.21.7 candidate.

Synthetic credentials are `dag.SetSecret` values mounted only with
`WithSecretVariable`. No stable secret cache key is supplied. The secret-bearing
steps occur after source/dependency layers. Failure artifacts export only a
redacted JSON summary and scan exact fixture secrets and forbidden headers.

`tools/ci/verify-dagger-log-health.sh` rejects `NullPointerException`, uncaught
exceptions, JVM thread exceptions, panic, fatal startup and runtime payload
`ERROR`/`FATAL`. Dagger structural `ERROR` spans are not application logs;
expected-failure payloads require the exact `PHASE-D EXPECTED <classification>`
marker. Two exact PostgreSQL errors are allowlisted only for Keycloak's empty-DB
bootstrap probes (`migration_model` and `public.databasechangeloglock` absent);
the trace must subsequently show Liquibase schema initialization and successful
Keycloak readiness. The approval-gated payment mapper NPE was independently
reproduced and fixed without inventing a business status before the FSM starts.

## Generation and observability

`dagger develop -y` is the official ignored Go binding regeneration process.
Before/after hashes of both generated binding files were identical, and
`dagger check --generate` produced no changeset. Module self-verification
compiles the generated root package, runs pure/command tests, vet and gofmt.

Plain progress traces are the local OpenTelemetry-derived audit surface. Cold
means the first result graph after a source revision; dependency volumes may
already be warm and are never globally deleted. Warm means an explicit Dagger
`CACHED` result, not merely a shorter wall time. No single-run timing is an SLA.
Runtime logs and exported artifacts stay under `/tmp`, outside the repository.

## Engine decision

The repository remains at `v0.21.4`. Candidate `v0.21.7` passed frozen image
lookup, `fast` and the service-binding DNS regression under `--x-release`.
Release fixes in 0.21.5–0.21.7 are desirable, but changing only
`engineVersion` makes ordinary repository commands fail because the installed
CLI is still 0.21.4. The upgrade is therefore `DEFER`, not rejected: update the
host CLI and module Engine atomically in a dedicated change, regenerate
bindings, repeat frozen lock/DNS/cache/workspace/full Phase D proof, and review
the open Go proxy issue first.

Primary external references and issue decisions are recorded in
[the dated architecture review](DAGGER-ARCHITECTURE-REVIEW-2026-07-23.md).

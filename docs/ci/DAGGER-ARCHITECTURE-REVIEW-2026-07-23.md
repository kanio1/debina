# Dagger architecture review — 2026-07-23

## Independent assessment

The earlier `PHASE-D-COMPLETE` proved its named journeys, but it did not prove
an optimal automatic-check surface. The implementation was functionally useful
yet an unfiltered `dagger check` exposed nested automatic roots and could launch
the same heavy graphs several times. The review also found stale workspace
function caching, no native image lockfile, an ambiguous `all`, an overclaimed
cache assertion and an approval-list mapper NPE.

## Findings

| ID | Severity | Area | Decision and status |
|---|---|---|---|
| DGR-001 | P0 | check topology | ADOPT: one canonical `PhaseD` check; fixed and statically guarded |
| DGR-002 | P0 | workspace correctness | ADOPT: auto-injected constructor Workspace; dirty-source digest proof passed |
| DGR-003 | P1 | image reproducibility | ADOPT: centralized images, `.dagger/lock`, frozen fail-closed proof |
| DGR-004 | P1 | `all` semantics | ADAPT Model A: `all-socket-free`, legacy `all`, typed-socket `full-local` |
| DGR-005 | P1 | cache evidence | ADOPT: external cold/warm/changed trace runner; equal output proves only determinism |
| DGR-006 | P1 | Engine | DEFER 0.21.7 until CLI+Engine can move atomically; candidate focused proofs passed |
| DGR-007 | P1 | runtime log health | ADOPT: exact scanner plus reproduced/fixed pre-FSM mapper NPE |
| DGR-008 | P2 | source precision | ADOPT: backend/frontend/Dagger/governance boundaries and manifest-only pnpm layer; focused/full proofs restored exact AsyncAPI and realm fixtures |
| DGR-009 | P2 | cache volumes | ADAPT: explicit SHARED with isolated concurrent writer/reset proof |
| DGR-010 | P2 | services | ADOPT: alias/DNS regression on current and candidate Engines |
| DGR-011 | P2 | generators | ADAPT: ignored bindings regenerated idempotently; frontend output compared in graph |
| DGR-012 | P2 | observability | ADOPT: plain traces, vertex state, wall-time context, no timing SLA |
| DGR-013 | P3 | workspace migration | DEFER the newer `dagger.toml`/SDK-module workspace model to a separate program |
| DGR-014 | P3 | remote CI | DEFER: Phase E is not authorized |

## External recommendation matrix

| Source | Applies to | Problem | Fit | Decision | Acceptance proof |
|---|---|---|---|---|---|
| [Checks](https://docs.dagger.io/core-concepts/checks/) | current/stable | all discovered checks run concurrently | direct | ADOPT | `check -l`, topology probe, unfiltered trace |
| [Go SDK 0.21.7](https://docs.dagger.io/0.21.7/extending/sdks/go/) | candidate line | Workspace must be an injected input; regeneration lifecycle | direct | ADOPT/ADAPT | dirty tracked-file digest, `develop` hashes |
| [Lockfiles](https://docs.dagger.io/reference/cli/lockfiles/) | current/stable | mutable registry resolution | direct | ADOPT | live population, frozen complete/missing |
| [Cache volumes](https://docs.dagger.io/extending/cache-volumes/) | stable | sharing/concurrent writers | direct | ADAPT | isolated SHARED stress |
| [Services](https://docs.dagger.io/extending/services/) | stable | lazy lifecycle, aliases and health | direct | ADOPT | DNS/HTTP binding regression |
| [Secrets](https://docs.dagger.io/extending/secrets/) | stable | plaintext/cache leakage | direct | ADOPT | redaction and log scan |
| [Upgrade to Workspaces](https://docs.dagger.io/next/reference/upgrade-to-workspaces/) | next | future workspace/module layout | not current Phase D | DEFER | dedicated migration plan |
| [v0.21.5](https://github.com/dagger/dagger/releases/tag/v0.21.5), [v0.21.6](https://github.com/dagger/dagger/releases/tag/v0.21.6), [v0.21.7](https://github.com/dagger/dagger/releases/tag/v0.21.7) | candidate | cache, filtered-directory, GC/CNI fixes | valuable | DEFER upgrade | CLI+Engine atomic full matrix |
| [#13169](https://github.com/dagger/dagger/issues/13169) | 0.18–0.20 report, open | `WithServiceBinding` plus `WithExec` DNS | relevant hypothesis | ADAPT regression | passes 0.21.4 and 0.21.7 |
| [#13060](https://github.com/dagger/dagger/issues/13060) | current, open | PRIVATE cache and lazy service identity | relevant | REJECT blanket PRIVATE | retain SHARED/stress |
| [#13246](https://github.com/dagger/dagger/issues/13246) | 0.21, open | detached module objects | no used detached object | MONITOR | full function/check matrix |
| [#13247](https://github.com/dagger/dagger/issues/13247) | 0.21, closed | container ownership regression | potentially relevant | MONITOR | generated/service proof |
| [#13596](https://github.com/dagger/dagger/issues/13596) | 0.21.7, open | Go proxy/module resolution | no private Go module today | DEFER condition | fresh module generation |

Community posts were not used as implementation authority.

## Engine compatibility matrix

| Area | Current 0.21.4 | Candidate 0.21.7 | Risk | Required proof |
|---|---|---|---|---|
| Podman | baseline proven | focused calls proven | host CLI mismatch | atomic CLI/Engine install |
| service binding | DNS proof passes | DNS proof passes | open historical issue | keep regression |
| Workspace | constructor proof passes | focused digest passes | future config migration | dirty-source matrix |
| checks / `Expect: FAILURE` | used by Phase D | no focused regression observed | semantic drift | full assurance trace |
| lockfile | live/frozen passes | frozen probe passes | format/lookup drift | missing-entry fail closed |
| Go SDK | generated for 0.21.4 | `fast` via x-release passes | host CLI rejects required module version | regenerate with installed CLI |
| secrets/cache | current proofs pass | release fixes attractive | cache behaviour changes | redaction + cache traces |

## Performance baseline

The table is observational, not an SLA. Cold is a new source-dependent result
graph with already-populated dependency volumes; warm requires an explicit
top-level `CACHED` span.

| Gate | Cold wall | Warm wall | Observation |
|---|---:|---:|---|
| `fast` | 31.76s | 1.14s | cold graph span 28.7s; warm top-level cached |
| `integration` | ~20.9s | 1.22s | fixed graph span 16.0s; first attempt exposed missing AsyncAPI boundary |
| `smoke` | 67.69s | 1.13s | one D3A Playwright invocation |
| `phase-d` | 203.04s | 1.13s | three D3B Playwright commands, sequential |

The redacted failure summary is 573 bytes. Service startup and browser vertex
durations remain in `/tmp/debina-perf-*.log`; they are not committed because
traces are runtime artifacts and may contain operational detail.

The first log-health pass also found the two expected PostgreSQL missing-table
probes used by Keycloak before it initializes an empty database. Only the exact
`migration_model` and `public.databasechangeloglock` relations are classified
as bootstrap observations; all other runtime `ERROR/FATAL` payloads remain
fail-closed.

## Final runtime matrix

The final unfiltered `dagger check --progress=plain` completed successfully on
Engine 0.21.4 in 250.92 seconds after the last source-boundary fix. Its trace
contains one logical vertex for D3A and one for each of the three D3B journeys;
their start/completion order proves sequential browser execution. Repeated
progress lines reuse those vertex identifiers and are not additional
executions.

Each public socket-free function (`fast`, `integration`, `smoke`,
`smoke-payments`, `phase-d`, and legacy `all`) then returned exit 0. The
explicit typed-socket Testcontainers regression ran 542 tests with no failures,
errors, or skips; its immediate repeat was top-level `CACHED`. Frozen image
lookup, cold/warm/changed-input cache traces, cache-volume reset/stress,
service-binding DNS, failure redaction, unexpected-failure propagation,
generator idempotence, and positive/negative log-health proofs also passed.

The final 250.92-second canonical result is a correctness observation, not a
performance regression or SLA: it includes work invalidated by the final
Keycloak fixture boundary correction, whereas the 203.04-second row above was
captured on the preceding source revision.

## Deferred remote CI proposal boundary

No remote CI implementation is authorized. A later Phase E proposal should be a
thin trigger that calls Dagger, declares the exact Podman/Docker runtime and
typed socket capability, supplies secrets through the runner provider, selects
frozen locks, retains only redacted summaries/traces for a bounded period, and
defines branch protection only after the local unfiltered proof remains stable.
It must not copy orchestration into YAML or enable shared remote cache without a
separate credential, tenancy and retention review.

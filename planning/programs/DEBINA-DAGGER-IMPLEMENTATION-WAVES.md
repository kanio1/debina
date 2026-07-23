# Debina Phase D Dagger implementation waves

Source: `planning/audits/DEBINA-DAGGER-REMAINING-PIPELINES-AUDIT.md`, ADR-N16,
`docs/ci/DAGGER-CHECK-MANIFEST.yaml`, and current Dagger source. All work remains
local, no remote CI, `act`, deployment, Phase E, Wave 12, realm-export mutation,
migration mutation, or generated-javadoc staging.

| Wave | Prerequisites / owned files | Exact implementation and proof | First failure / timeout | Staged boundary / commit / rollback / terminal status |
|---|---|---|---|---|
| D0 credential authority | D3A proof; `dagger/credentials.go`, `smoke.go`, `integration.go`, pure tests | create one typed bundle; replace plaintext Dagger password env/Maven args; preserve authorities; `go test ./...`, `dagger check fast` | `SECRET_AUTHORITY`, 5m | only Dagger files; `refactor(ci): centralize phase d credential authority`; revert one commit; DONE only on proof |
| D1 shared runtime | D0; `smoke_runtime.go`, Dagger tests/docs | one local service instance per leaf, markers before backend, scheduler config explicit; `dagger call smoke-backend-readiness` | `READINESS_TIMEOUT`, 3m | runtime files only; `refactor(ci): add phase d smoke runtime`; revert D1; DONE |
| D2 JSON_DIRECT | D1; `smoke_payments.go`, D3B spec, evidence helper/manifest/docs | UI → BFF → REST → PG/ISO/outbox/Kafka proof; `dagger call smoke-json-direct-submission --progress=plain` | `BFF_CONTRACT`, 5m | leaf plus its spec/evidence only; `test(ci): add json direct dagger smoke`; revert D2; DONE |
| D3 maker-checker | D1; its leaf/spec/fixture/evidence | source-shaped rule fixture; two real BrowserContexts; self-denial, checker approval, audit/history/Kafka; command above | `AUTHORIZATION_BOUNDARY`, 6m | only maker-checker files; `test(ci): add maker checker dagger smoke`; revert D3; DONE |
| D4 Payment Detail | D1; its leaf/spec/evidence | independent JSON_DIRECT creation, Detail/timeline/ISO/evidence drawer and DB/Kafka correlation; command above | `DATABASE_LINEAGE`, 5m | only detail files; `test(ci): add payment detail lineage dagger smoke`; revert D4; DONE |
| D5 smoke aggregation | D2–D4; `checks.go`, manifest/docs | add `SmokeAuth`, `SmokePayments`; preserve `Smoke`; run each then new aggregates | `CHECK_COMPOSITION`, 10m | aggregation files only; `feat(ci): compose phase d payment smoke checks`; revert D5; DONE |
| D6 D4 propagation | D1; `resilience.go`, pure tests | unavailable dependencies, non-zero and bounded timeout leaves; each expected failure is asserted by a parent finite proof | `FALSE_SUCCESS`, 5m each | resilience files only; `test(ci): add phase d failure propagation probes`; revert D6; DONE |
| D7 D4 diagnostics | D6; artifact policy/docs | redacted artifacts and teardown proof; verify forbidden strings absent | `SECRET_EXPOSURE`, 5m | diagnostic files only; `test(ci): harden phase d diagnostics`; revert D7; DONE |
| D8 D4 cache | D1; cache functions/tests/docs | cold/warm, source/config/secret dependency observations; never cache runtime state | `CACHE_CONTRACT`, measured | cache files only; `test(ci): prove phase d cache boundaries`; revert D8; DONE |
| D9 final aggregate | D5–D8; `checks.go`, manifest/docs | `PhaseD` composes socket-free leaves, names Testcontainers separately; `dagger check phase-d` then `dagger check` when manifest permits | `AGGREGATE_DUPLICATION`, 20m | aggregate only; `feat(ci): add phase d socket free aggregate`; revert D9; DONE |
| D10 closeout | D9; planning/docs/HANDOFF only | manifest/implementation evidence, `git diff --check`, protected SHA, realm diff, staged review | `DOCUMENTATION_STALE`, 5m | documentation-only; `docs(ci): close phase d`; revert D10; DONE |

Each wave must leave unrelated user changes untouched. A Dagger Engine/Podman prerequisite failure is
`INFRASTRUCTURE-BLOCKED`, not a justification for Docker installation, a TCP socket, root execution,
SELinux changes, or weaker security.

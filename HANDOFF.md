# HANDOFF

## Zadanie

Debina is a synthetic enterprise SEPA/ISO 20022 research platform. On `rebase/enterprise-evolution`, Phase D now has a proven explicit Dagger typed-Socket Testcontainers regression; the remaining ADR-N16 browser smoke work is capability-blocked by the repository’s testing/runtime gate.

## Zrobione

- D2B portability correction is committed in `d763b9e`: `backend/src/test/java/com/sepanexus/settlement/SettlementFinalityServiceTest.java` reads `finality_at timestamptz(3)` through typed JDBC as `OffsetDateTime`, converts it to `Instant`, and asserts exact equality with `2026-07-20T10:15:30.123Z`. It neither sets a timezone nor compares formatted text.
- `dagger/testcontainers.go` adds the explicit `testcontainers-finality-portability` function. Its targeted Testcontainers proof passed 5/0/0; the complete explicit `testcontainers-regression --runtime-socket=/run/podman/podman.sock` cold run passed 540 tests, 0 failures/errors/skips, Maven 4m29s. The immediate warm run exited 0 in about one second through Dagger’s result cache; it retained the required typed socket argument and did not rerun Maven.
- `dagger check fast` passed in 17.6s and socket-free `dagger check integration` passed in 16.9s with 128 Maven tests. The D2B command contract and cache evidence are recorded in `docs/ci/DAGGER-IMPLEMENTATION.md` and `docs/ci/DAGGER-CHECK-MANIFEST.yaml`.
- D3 inspected ADR-N16, `docs/ci/SMOKE-CAPABILITY-MATRIX.*`, current BFF routes/locators, EPIC-24 and the frontend package. All six capped browser journeys are classified `CAPABILITY-BLOCKED`: there is no approved Playwright package/configuration or Dagger smoke runtime graph, and EPIC-24 sequencing remains blocked on the missing Control Room. No Playwright, product code or runtime-compose graph was created.
- The user-owned `build/generated-spring-modulith/javadoc.json` remains unstaged but externally drifted during this session to SHA-256 `47b1b89f63804b4062cd6abe9242a7d56b2212636de95a64784d53723c03e054` (recorded checkpoint SHA-256 was `9c484e010bfa0a8719f78dd4ade744fe7e08a3a9fe7eaf0fb35ed1dd2ca0a015`). It was not restored, staged or otherwise modified.

## Utknęliśmy na

`CAPABILITY-BLOCKED-CHECKPOINT`: D3/D4 cannot honestly run supported smoke. ADR-N16 permits only six named smoke journeys but does not itself authorize a new testing/runtime foundation; `planning/epics/EPIC-24-frontend-screens.md` requires the unimplemented Ops Control Room before Playwright, while `frontend/package.json` has no Playwright command/dependency/configuration. Health additionally has no Dagger runtime probe. Separately, resolve the user-owned javadoc drift outside this Phase D scope; do not bypass either boundary by inventing a harness, adding product behavior, using runtime compose or restoring the file.

## Plan na następny krok

Obtain a source-backed decision that resolves the EPIC-24 sequencing/runtime-test-foundation gate for ADR-N16 smoke; then materialize only the approved Dagger smoke graph and six capped journeys before resuming D4.

## Pułapki, których nie wolno powtórzyć

- The runtime socket remains a required explicit argument only: `dagger call testcontainers-regression --runtime-socket=/run/podman/podman.sock`; never put it in no-argument `fast` or `integration`, make it optional, copy/proxy it, use Docker, or change host configuration.
- Do not reinterpret Dagger’s warm result cache as a second Maven execution; the cold 540-test run is execution evidence and the warm hit is cache evidence.
- Do not add `TZ`, PostgreSQL session timezone, string timestamp checks or a Warsaw execution contract. The finality assertion must stay typed JDBC `OffsetDateTime` → exact `Instant`.
- Do not create Playwright tests/runtime graph until the recorded capability gate is resolved. Do not touch Wave 12, Phase E, remote CI, `act`, deployment, production payment code, migrations, runtime compose, Keycloak realm or the user-owned javadoc file.

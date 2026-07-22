# HANDOFF

## Zadanie

Debina is a synthetic enterprise SEPA/ISO 20022 research platform. On `rebase/enterprise-evolution`, Phase D has a proven explicit Dagger typed-Socket Testcontainers regression and an in-progress ADR-N16 D3A smoke runtime.

## Zrobione

- D2B portability correction is committed in `d763b9e`: `backend/src/test/java/com/sepanexus/settlement/SettlementFinalityServiceTest.java` reads `finality_at timestamptz(3)` through typed JDBC as `OffsetDateTime`, converts it to `Instant`, and asserts exact equality with `2026-07-20T10:15:30.123Z`. It neither sets a timezone nor compares formatted text.
- `dagger/testcontainers.go` adds the explicit `testcontainers-finality-portability` function. Its targeted Testcontainers proof passed 5/0/0; the complete explicit `testcontainers-regression --runtime-socket=/run/podman/podman.sock` cold run passed 540 tests, 0 failures/errors/skips, Maven 4m29s. The immediate warm run exited 0 in about one second through Dagger’s result cache; it retained the required typed socket argument and did not rerun Maven.
- `dagger check fast` passed in 17.6s and socket-free `dagger check integration` passed in 16.9s with 128 Maven tests. The D2B command contract and cache evidence are recorded in `docs/ci/DAGGER-IMPLEMENTATION.md` and `docs/ci/DAGGER-CHECK-MANIFEST.yaml`.
- Governance exception commit `73cb93a` authorizes the narrow local D3A Chromium/Dagger foundation without completing EPIC-24 or unblocking Control Room/general acceptance.
- D3A PostgreSQL/Flyway readiness passed. Keycloak 26.6.4 initialized its database, imported `sepa-nexus`, and became ready after the service command was moved from non-terminating `WithExec` to `AsService.Args` with `UseEntrypoint: true`. Its inherited 9000/8443 ports were removed; only 8080 remains. `dagger call smoke-keycloak-readiness stdout --progress=plain` passed in 29.28s, including automatic TCP readiness and a short-lived alias-bound realm discovery probe. `gofmt`, `go test ./...`, and `go vet ./...` passed.
- The user-owned `build/generated-spring-modulith/javadoc.json` remains unstaged but externally drifted during this session to SHA-256 `47b1b89f63804b4062cd6abe9242a7d56b2212636de95a64784d53723c03e054` (recorded checkpoint SHA-256 was `9c484e010bfa0a8719f78dd4ade744fe7e08a3a9fe7eaf0fb35ed1dd2ca0a015`). It was not restored, staged or otherwise modified.

## Utknęliśmy na

Backend readiness passed: the original missing `sepa_app` error was a Dagger graph-ordering defect. Flyway migrate+validate now creates `/tmp/d3a-flyway-complete`, which is mounted only as a dependency marker before the backend service. The shared PostgreSQL service has roles before startup; Kafka joined; 8081 and alias-bound `/actuator/health` passed in 44.39s (exit 0). Frontend readiness is next; do not start Chromium.

## Plan na następny krok

Commit the coherent backend readiness correction, then isolate the pinned production frontend using the same backend and Keycloak instances.

## Pułapki, których nie wolno powtórzyć

- The runtime socket remains a required explicit argument only: `dagger call testcontainers-regression --runtime-socket=/run/podman/podman.sock`; never put it in no-argument `fast` or `integration`, make it optional, copy/proxy it, use Docker, or change host configuration.
- Do not reinterpret Dagger’s warm result cache as a second Maven execution; the cold 540-test run is execution evidence and the warm hit is cache evidence.
- Do not add `TZ`, PostgreSQL session timezone, string timestamp checks or a Warsaw execution contract. The finality assertion must stay typed JDBC `OffsetDateTime` → exact `Instant`.
- Do not start frontend/Chromium, D3B, D4, Phase E, Wave 12, remote CI, `act`, deployment, product payment code, migrations, runtime compose, Keycloak realm or the user-owned javadoc file. Never stage the externally drifted javadoc file.

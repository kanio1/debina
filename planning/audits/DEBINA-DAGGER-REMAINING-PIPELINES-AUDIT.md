# Debina Dagger remaining-pipelines audit

## Scope and evidence boundary

This is a design audit at `7475475b9b874ef0ae50a8cc24c225a59587e94e` on
`rebase/enterprise-evolution`. `7475475` is `HEAD` and an ancestor of `HEAD`.
It does not run Chromium, a payment journey, remote CI, `act`, or a deployment.
The Dagger Engine could not start in this sandbox: Podman reports a read-only
`/run/user/1000/libpod` sticky-bit operation. Consequently, current-command
results below are source-backed unless explicitly marked historical runtime
evidence from `docs/ci/DAGGER-IMPLEMENTATION.md`.

Protected generated file SHA-256 before design: `47b1b89f63804b4062cd6abe9242a7d56b2212636de95a64784d53723c03e054`
for `build/generated-spring-modulith/javadoc.json`. Wave 12 (`wip/wave-12-signature-verdict-evidence`,
`5ebebb0`) was not checked out or modified.

## Verified current foundation

| Claim | Evidence | Status |
|---|---|---|
| `fast` exists | `dagger/checks.go: Fast`, manifest `fast.implemented` | SOURCE-PROVEN |
| socket-free `integration` exists | `Integration` composes Maven subset, production build, PG/Flyway/RLS and Kafka probe; no `Socket` argument | SOURCE-PROVEN |
| Testcontainers socket is explicit and typed | `testcontainers.go` accepts `*dagger.Socket`, mounts it only in the Testcontainers Maven container | SOURCE-PROVEN |
| D3A login/session/health exists | `smoke.go: SmokeLoginSessionHealth`, `frontend/e2e/d3a-vertical-smoke.spec.ts` | SOURCE-PROVEN; historical RUNTIME-PROVEN |
| D3A requires `sub` and `payment_submitter` | D3A spec assertions | SOURCE-PROVEN |
| `preferredUsername` is nullable | D3A asserts `null`; realm's `sepa-web` scopes are `basic`, `sepa-guc`, not `profile` | SOURCE-PROVEN |
| canonical realm remains unchanged | only `d3aRealmOverlayArtifacts` derives an in-graph overlay; pure verifier permits only two aliases | SOURCE-PROVEN |
| D3B and D4 are incomplete | manifest lists three D3B journeys as pending; no D3B/D4 public functions or specs | SOURCE-PROVEN |

Reusable patterns are: one service instance retained in local variables; Flyway and credential marker files made by the finite producer; Keycloak overlay as a derived, fail-closed artifact; `AsService(Args: ...)` for long-lived processes; alias-only readiness clients; `WithSecretVariable`; named dependency caches only; and a finite Playwright command behind `pure.D3AReadinessCommand`.

## Capability inventory

| Capability | Source entry point | UI/BFF path | Backend endpoint | Database evidence | Kafka evidence | Existing tests | Runtime status |
|---|---|---|---|---|---|---|---|
| JSON_DIRECT submission | `PaymentController.submit`; `PaymentService`; `JsonDirectLineageRecorder` | `/payments` form → `POST /api/payments` | `POST /api/v1/payments`, Idempotency-Key required | `payment.payments`, `payment.payment_approvals`, `iso.iso_messages`, `iso.payment_iso_identifiers`, `iso.message_lineage`, `payment.outbox_events`, history/events | `payment.received` through `OutboxDispatcher` | `JsonDirectIngestionTest`, `PaymentControllerTest`, `WalkingSkeletonIntegrationTest` | SOURCE-PROVEN; not Dagger-runtime-proven |
| maker-checker approval | `ApprovalSubmissionGate`; `ApprovalDecisionService` | `ApprovalQueue`; BFF approve/reject routes | `POST /api/v1/payments/{id}/approve|reject` | `payment.payment_approvals`, payment history/events, `audit.audit_log`, payment outbox | `payment.received` after approval only | `ApprovalSubmissionIntegrationTest`, `ApprovalDecisionKeycloakRuntimeTest`, GraphQL/BFF tests | SOURCE-PROVEN; capability fixture required |
| Payment Detail / lineage | `PaymentService.paymentDetail`, `PaymentTimelineLookup`, GraphQL evidence queries | `/payments/[id]`; evidence drawer → `/api/graphql` | `GET /api/v1/payments/{id}`, `/timeline`; GraphQL | payment, ISO identifier/lineage, history/events, audit | correlation is payment id in `payment.received` payload/key; not a separate UI Kafka model | `PaymentControllerTest`, `PaymentLineageGraphQLTest`, `IsoPaymentEvidenceQueryIntegrationTest` | SOURCE-PROVEN |
| approval queue GraphQL | `ApprovalGraphQlController` | BFF GraphQL allow-list → `ApprovalQueue` | `/graphql` query only | `payment.payment_approvals` joined through payment RLS | none | `ApprovalGraphQlRuntimeTest`, ownership/read-only tests | SOURCE-PROVEN |

Fresh Flyway creates no approval-matrix row. `ApprovalMatrixEvaluator` therefore selects `NOT_REQUIRED`; a pending approval cannot arise without a tenant-scoped fixture. `ApprovalSubmissionIntegrationTest.addBroadRule` proves the supported fixture shape: login as `reference_data_role`, set `app.tenant_id`, insert exactly one current broad rule with `requires_approval=true`, then let the real submitter create the payment. This is test setup, not a new product capability. D3A sets `SEPA_SCHEDULING_ENABLED=false`; D3B must set it true and bound `SEPA_SCHEDULING_RELAY_FIXED_DELAY_MS` so the existing relay produces the required Kafka proof.

## Test ownership and pipeline allocation

| Test or group | Current command | Dependencies | Socket | Proposed pipeline | Duplicate/missing |
|---|---|---|---|---|---|
| Go pure/Dagger composition | `go test ./...` in Dagger | none | no | `fast` | retain |
| governance, backend compile/architecture, frontend codegen/lint/typecheck | existing `fast` commands | source/toolchains | no | `fast` | retain |
| socket-free backend subset, frontend production build | existing `integration` | containers only | no | `integration` | retain |
| Flyway fresh/upgrade, grants/RLS | existing `integration` | ephemeral PostgreSQL | no | `integration` | retain |
| Kafka non-product probe | existing `integration` | ephemeral Kafka | no | `integration` | retain; does not prove payment event |
| D3A Playwright | `pnpm run test:smoke:d3a` | PG/Kafka/Keycloak/backend/frontend | no | `SmokeAuth` / compatibility `Smoke` | retain exactly |
| JSON_DIRECT vertical evidence | no leaf | full ephemeral graph, scheduler | no | `SmokeJsonDirectSubmission` | missing |
| maker-checker vertical evidence | no leaf | full graph + reference-data fixture | no | `SmokeMakerCheckerApproval` | missing |
| detail/evidence vertical evidence | no leaf | full graph + seeded source payment | no | `SmokePaymentDetailLineage` | missing |
| Testcontainers suite | `dagger call testcontainers-regression --runtime-socket=...` | host Podman socket | explicit typed socket only | unchanged explicit regression | intentionally excluded from aggregate |

## Podman and credential findings

The baseline records rootful Podman 5.8.4 and `CONTAINER_HOST`/`DOCKER_HOST` as
`unix:///run/podman/podman.sock`. The live sandbox cannot query Podman because
its runtime directory is read-only; no host change is authorized. There are no
host ports or socket mounts in normal graphs. `infra/docker-compose.yml` is a
developer topology, not a Dagger service graph. No Quadlet source exists.

| Logical credential | Source authority | Current Dagger identifier | Consumers | Exposure risk | Proposed authority |
|---|---|---|---|---|---|
| migration role | `V1__roles.sql`; `application.yml` | none; plain Maven args/env | Flyway, PG probes | password appears in `-Dflyway.password` and env | one `credentials.migrationPassword` secret |
| application role | `V1__roles.sql`; app config | `d3a-sepa-app-db-password` | backend, credential probe | plain `PGPASSWORD` remains in integration | one `credentials.appPassword` secret |
| reference-data fixture role | reference-data role migration/tests | none | approval-rule fixture | must never be placed in SQL command | one `credentials.referenceDataPassword` secret |
| outbox dispatcher | role migration; app config | none | D3B backend | backend currently receives default ordinary config | one `credentials.outboxPassword` secret |
| Keycloak database/admin | compose + D3A construction | repeated `dag.SetSecret` for DB password | Keycloak PG/Keycloak | repeated construction | one shared secret object each |
| web client | canonical realm + frontend config | `d3a-keycloak-client-secret` | frontend BFF | authoritative realm deliberately contains development value | one `credentials.webClientSecret` secret |
| submitter/approver | canonical realm users | D3A submitter only | Playwright | repeated `dag.SetSecret`, no approver design | typed username/password secrets per actor |

The canonical realm and immutable migrations must remain source authorities; the pipeline may only materialize their known development values as Dagger secrets. This resolves repeated secret creation and plaintext Maven arguments, but it does not claim the committed development authorities are production secret management.

## Contradictions and constraints

`dagger/checks.go` still comments that Testcontainers has no supported bridge, while `testcontainers.go` and the implementation document prove the explicit typed-socket bridge. The comment is stale; no behavior change is required in this design. The requested D3B leaves are not capability-blocked. D4 cannot prove a secret-dependent cache invalidation by observing secret text; it can only compare redacted execution/cache markers and assert no secret appears. That is a genuine Dagger evaluation contract to validate during implementation.

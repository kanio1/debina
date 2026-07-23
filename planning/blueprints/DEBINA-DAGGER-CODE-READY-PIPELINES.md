# Debina Phase D — code-ready remaining Dagger pipelines

## Decision and public surface

`Acceptance` is the final socket-free aggregate and the sole automatic
`+check`. It runs `Fast` and `Integration` in parallel, then `SmokeSuite`
sequentially. `PipelineAssurance` is an independent public gate.
`SmokeSuite` owns the full capped ADR-N16 browser scope; `SmokeAuth` and
`SmokePayments` remain narrower diagnostic gates. `PhaseD`, `AllSocketFree`,
legacy `All` and `Smoke` remain deprecated callable aliases. `FullLocal` adds
`BackendTestcontainers` exactly once and therefore requires a typed socket.

```text
dagger call fast
dagger call integration
dagger call smoke-auth
dagger call smoke-json-direct-submission
dagger call smoke-maker-checker-approval
dagger call smoke-payment-detail-lineage
dagger call smoke-payments
dagger call smoke-suite            # complete capped ADR-N16 browser smoke
dagger call pipeline-assurance
dagger call acceptance             # parallel Fast + Integration, then SmokeSuite
dagger check                       # discovers only Acceptance
dagger call backend-testcontainers --runtime-socket=/run/podman/podman.sock
dagger call full-local --runtime-socket=/run/podman/podman.sock
```

The prior design note that both `SmokePayments` and `PhaseD` should become
checks is superseded by the runtime topology review. Nested discovered checks
repeat child graphs; `dagger/pure/check_topology_test.go` enforces the single
`Acceptance` root and the compositor rejects duplicate classifications.

| Deprecated callable | Canonical replacement |
|---|---|
| `Smoke` | `SmokeSuite` |
| `PhaseD`, `AllSocketFree`, `All` | `Acceptance` |
| `TestcontainersRegression` | `BackendRegressionAll` |
| `CacheReuse` | `CacheOutputDeterminism` |
| `CacheInvalidation` | `CacheOutputInputSensitivity` |

## Proposed file tree

```text
dagger/
  checks.go                  # final staged Acceptance, assurance and aliases
  credentials.go             # internal typed secret bundle
  smoke_runtime.go           # one graph, readiness, finite runners
  smoke_evidence.go          # PostgreSQL/Kafka finite clients and markers
  smoke_payments.go          # three public D3B leaves
  resilience.go              # D4 finite leaves
  pure/smoke.go              # bounded, redacting command constructors + unit tests
frontend/e2e/
  d3b-json-direct-submission.spec.ts
  d3b-maker-checker-approval.spec.ts
  d3b-payment-detail-lineage.spec.ts
```

## Exact compile-oriented runtime design

The following uses methods present in the generated v0.21.4 binding:
`WithServiceBinding`, `WithSecretVariable`, `WithFile`, `WithDirectory`,
`WithMountedCache`, `AsService`, `Stdout` and `Sync`. It reuses existing images,
aliases, `postgresService`, `kafkaService`, `smokeMigrations`,
`keycloakServiceWithOverlay`, and `smokeFrontendService`.

```go
// dagger/credentials.go
package main

import "dagger/debina-verification/internal/dagger"

type smokeCredentials struct {
	MigrationPassword *dagger.Secret
	AppPassword *dagger.Secret
	ReferenceDataPassword *dagger.Secret
	OutboxPassword *dagger.Secret
	KeycloakDBPassword *dagger.Secret
	KeycloakAdminPassword *dagger.Secret
	WebClientSecret *dagger.Secret
	SubmitterUsername *dagger.Secret
	SubmitterPassword *dagger.Secret
	ApproverUsername *dagger.Secret
	ApproverPassword *dagger.Secret
}

func newSmokeCredentials() smokeCredentials {
	return smokeCredentials{
		MigrationPassword: dag.SetSecret("phase-d-migration-password", "dev-only-migration"),
		AppPassword: dag.SetSecret("phase-d-sepa-app-password", "dev-only-app"),
		ReferenceDataPassword: dag.SetSecret("phase-d-reference-data-password", "dev-only-reference-data"),
		OutboxPassword: dag.SetSecret("phase-d-outbox-dispatcher-password", "dev-only-outbox-dispatcher"),
		KeycloakDBPassword: dag.SetSecret("phase-d-keycloak-db-password", "dev-only-keycloak-db"),
		KeycloakAdminPassword: dag.SetSecret("phase-d-keycloak-admin-password", "dev-only-admin"),
		WebClientSecret: dag.SetSecret("phase-d-keycloak-web-client-secret", "dev-only-sepa-web-secret"),
		SubmitterUsername: dag.SetSecret("phase-d-submitter-username", "submitter"),
		SubmitterPassword: dag.SetSecret("phase-d-submitter-password", "dev-only-submitter"),
		ApproverUsername: dag.SetSecret("phase-d-approver-username", "approver"),
		ApproverPassword: dag.SetSecret("phase-d-approver-password", "dev-only-approver"),
	}
}
```

```go
// dagger/smoke_runtime.go
package main

import "dagger/debina-verification/internal/dagger"

type smokeRuntime struct {
	postgres *dagger.Service
	kafka *dagger.Service
	keycloak *dagger.Service
	backend *dagger.Service
	frontend *dagger.Service
	migrationMarker *dagger.File
	credentials smokeCredentials
}

func (m *DebinaVerification) paymentSmokeRuntime(instance string) *smokeRuntime {
	creds := newSmokeCredentials()
	postgres := m.postgresService("payment-" + instance)
	kafka := m.kafkaService()
	overlay := m.d3aRealmOverlayArtifacts()
	keycloak := m.keycloakServiceWithOverlay(overlay) // revised to receive creds; same derived realm only
	migrationMarker := m.smokeMigrationMarker(postgres)
	backend := m.paymentSmokeBackendService(postgres, kafka, keycloak, migrationMarker, creds)
	frontend := m.smokeFrontendService(backend, keycloak) // revised to receive creds.WebClientSecret
	return &smokeRuntime{postgres, kafka, keycloak, backend, frontend, migrationMarker, creds}
}

func (m *DebinaVerification) paymentSmokeBackendService(postgres, kafka, keycloak *dagger.Service, marker *dagger.File, c smokeCredentials) *dagger.Service {
	credentialMarker := m.smokeAppCredentialMarker(postgres, marker, c.AppPassword)
	return dag.Container().From(javaImage).
		WithMountedCache("/root/.m2/repository", dag.CacheVolume("debina-maven-jdk25")).
		WithDirectory("/workspace", m.source()).WithFile("/workspace/.phase-d-flyway-complete", marker).
		WithFile("/workspace/.phase-d-sepa-app-credential-complete", credentialMarker).WithWorkdir("/workspace").
		WithServiceBinding(postgresServiceAlias, postgres).WithServiceBinding(kafkaServiceAlias, kafka).WithServiceBinding(keycloakServiceAlias, keycloak).
		WithEnvVariable("SEPA_APP_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").WithEnvVariable("SEPA_APP_DB_USER", "sepa_app").WithSecretVariable("SEPA_APP_DB_PASSWORD", c.AppPassword).
		WithEnvVariable("SEPA_MIGRATION_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("OUTBOX_RELAY_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").WithEnvVariable("OUTBOX_RELAY_DB_USER", "outbox_dispatcher_role").WithSecretVariable("OUTBOX_RELAY_DB_PASSWORD", c.OutboxPassword).
		WithEnvVariable("SEPA_SIGNATURE_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").WithEnvVariable("SEPA_LEDGER_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").WithEnvVariable("SEPA_SETTLEMENT_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").WithEnvVariable("GROSS_INSTANT_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").WithEnvVariable("APPROVAL_EXPIRY_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").WithEnvVariable("AUDIT_AUDITOR_DB_URL", "jdbc:postgresql://postgres:5432/sepa_nexus").
		WithEnvVariable("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092").WithEnvVariable("KEYCLOAK_ISSUER_URI", "http://keycloak:8080/realms/sepa-nexus").
		WithEnvVariable("SEPA_SCHEDULING_ENABLED", "true").WithEnvVariable("SEPA_SCHEDULING_RELAY_FIXED_DELAY_MS", "250").
		WithExposedPort(8081).AsService(dagger.ContainerAsServiceOpts{Args: []string{"./mvnw", "-f", "backend", "spring-boot:run"}})
}

func (m *DebinaVerification) paymentPlaywright(rt *smokeRuntime, command string) *dagger.Container {
	return dag.Container().From(playwrightImage).WithMountedCache("/pnpm/store", dag.CacheVolume("debina-pnpm-node24.18.0-pnpm10.33.0")).
		WithEnvVariable("PNPM_HOME", "/pnpm").WithEnvVariable("PNPM_STORE_DIR", "/pnpm/store").WithDirectory("/workspace", m.source()).WithWorkdir("/workspace/frontend").
		WithServiceBinding(backendServiceAlias, rt.backend).WithServiceBinding(keycloakServiceAlias, rt.keycloak).WithServiceBinding(frontendServiceAlias, rt.frontend).
		WithEnvVariable("SMOKE_BASE_URL", "http://frontend:3000").WithSecretVariable("SMOKE_SUBMITTER_USERNAME", rt.credentials.SubmitterUsername).WithSecretVariable("SMOKE_SUBMITTER_PASSWORD", rt.credentials.SubmitterPassword).WithSecretVariable("SMOKE_APPROVER_USERNAME", rt.credentials.ApproverUsername).WithSecretVariable("SMOKE_APPROVER_PASSWORD", rt.credentials.ApproverPassword).
		WithEnvVariable("PLAYWRIGHT_BROWSERS_PATH", "/ms-playwright").WithExec([]string{"corepack", "enable", "pnpm"}).WithExec([]string{"pnpm", "config", "set", "store-dir", "/pnpm/store"}).WithExec([]string{"pnpm", "install", "--frozen-lockfile"}).
		WithExec([]string{"sh", "-ec", pure.D3AReadinessCommand([]string{"http://keycloak:8080/realms/sepa-nexus/.well-known/openid-configuration", "http://backend:8081/actuator/health", "http://frontend:3000"}, command)})
}
```

The implementation must change `keycloakPostgresService`, `keycloakServiceWithOverlay`, and
`smokeFrontendService` to receive the single bundle rather than calling `dag.SetSecret` internally.
That is a credential-authority refactor, not a realm/migration change.

## D3B public leaves and evidence clients

```go
// dagger/smoke_payments.go
func (m *DebinaVerification) SmokeJsonDirectSubmission(ctx context.Context) (string, error) {
	rt := m.paymentSmokeRuntime("json-direct")
	browser := m.paymentPlaywright(rt, "pnpm exec playwright test e2e/d3b-json-direct-submission.spec.ts --project=chromium --workers=1 && printf '%s\\n' 'D3B browser passed' >/tmp/d3b-browser.ok")
	return m.paymentEvidence(rt, browser.File("/tmp/d3b-browser.ok"), "D3B-JSON-DIRECT-0001", "PHASE-D JSON_DIRECT EVIDENCE VERIFIED").Stdout(ctx)
}
func (m *DebinaVerification) SmokeMakerCheckerApproval(ctx context.Context) (string, error) {
	rt := m.paymentSmokeRuntime("maker-checker")
	fixture := m.seedApprovalRule(rt)
	browser := m.paymentPlaywright(rt, "test -f /tmp/phase-d-approval-rule.marker && pnpm exec playwright test e2e/d3b-maker-checker-approval.spec.ts --project=chromium --workers=1 && printf '%s\\n' 'D3B browser passed' >/tmp/d3b-browser.ok").WithFile("/tmp/phase-d-approval-rule.marker", fixture)
	return m.paymentApprovalEvidence(rt, browser.File("/tmp/d3b-browser.ok"), "D3B-MAKER-CHECKER-0001").Stdout(ctx)
}
func (m *DebinaVerification) SmokePaymentDetailLineage(ctx context.Context) (string, error) {
	rt := m.paymentSmokeRuntime("detail-lineage")
	browser := m.paymentPlaywright(rt, "pnpm exec playwright test e2e/d3b-payment-detail-lineage.spec.ts --project=chromium --workers=1 && printf '%s\\n' 'D3B browser passed' >/tmp/d3b-browser.ok")
	return m.paymentEvidence(rt, browser.File("/tmp/d3b-browser.ok"), "D3B-DETAIL-LINEAGE-0001", "PHASE-D DETAIL-LINEAGE EVIDENCE VERIFIED").Stdout(ctx)
}
```

`seedApprovalRule` is a finite PostgreSQL client bound to `rt.postgres`, receives
`rt.migrationMarker` and only `ReferenceDataPassword`, and writes an opaque success marker
after `SET app.tenant_id` and one broad row for tenant
`00000000-0000-0000-0000-000000000001`. It must not print SQL connection strings,
passwords, a cookie, or a token. The proposal deliberately keeps each leaf independent.

After each browser test, the implementation appends a separate finite evidence client to the
same Dagger graph, passing the payment id by a marker file made by the test. The browser writes
only a UUID into `/tmp/phase-d-payment-id`; this is not a session artifact. A PostgreSQL client
must set no tenant GUC when using `sepa_migration` for cross-table test evidence, and assert exact
rows by payment id. A Kafka client must consume `payment.received` with a unique group id derived
from the leaf name, `--from-beginning --max-messages 20 --timeout-ms 30000`, and match the UUID in
the JSON payload; it must not log any browser/header data.

```sql
-- JSON_DIRECT / detail evidence
SELECT p.id, p.status, a.status AS approval_status, i.source_message_type, i.end_to_end_id,
       l.lineage_role, o.event_type, o.published_at
FROM payment.payments p
JOIN payment.payment_approvals a ON a.payment_id = p.id
JOIN iso.payment_iso_identifiers i ON i.payment_id = p.id
JOIN iso.message_lineage l ON l.payment_id = p.id
JOIN payment.outbox_events o ON o.aggregate_id = p.id
WHERE p.id = :'payment_id';

-- maker-checker evidence
SELECT a.status, a.maker_user_id, a.checker_user_id, a.decided_at,
       al.command_type, al.outcome, al.before_state ->> 'approvalStatus', al.after_state ->> 'approvalStatus'
FROM payment.payment_approvals a
JOIN audit.audit_log al ON al.payment_id = a.payment_id
WHERE a.payment_id = :'payment_id' ORDER BY al.occurred_at;

-- payment detail timeline identity
SELECT h.seq, h.to_status, h.event_type, h.event_ref, e.id AS event_id
FROM payment.payment_status_history h LEFT JOIN payment.payment_events e ON e.id = h.event_ref
WHERE h.payment_id = :'payment_id' ORDER BY h.seq;
```

Safe terminal markers: `PHASE-D JSON_DIRECT EVIDENCE VERIFIED`,
`PHASE-D MAKER-CHECKER EVIDENCE VERIFIED`, `PHASE-D DETAIL-LINEAGE EVIDENCE VERIFIED`.
First-failure classifications are `READINESS_TIMEOUT`, `AUTHORIZATION_BOUNDARY`,
`BFF_CONTRACT`, `DATABASE_LINEAGE`, `KAFKA_CORRELATION`, and `BROWSER_ASSERTION`.

## Complete Playwright skeletons

```ts
// shared local helpers belong in each spec until a tested common helper is introduced.
async function login(page: import("@playwright/test").Page, username: string, password: string) {
  await page.goto("/"); await page.getByLabel("Username or email").fill(username);
  await page.getByRole("button", {name:"Sign In"}).click();
  await page.locator('input#password[name="password"][type="password"]').fill(password);
  await page.getByRole("button", {name:"Sign In"}).click();
}
```

```ts
// d3b-json-direct-submission.spec.ts
import {expect,test} from "@playwright/test";
test("D3B JSON_DIRECT reaches BFF, backend, evidence and Kafka", async ({page}) => {
  const e2e = "D3B-JSON-DIRECT-0001";
  await login(page, process.env.SMOKE_SUBMITTER_USERNAME!, process.env.SMOKE_SUBMITTER_PASSWORD!);
  await page.getByTestId("payments.submit.end-to-end-id-input").fill(e2e);
  await page.getByTestId("payments.submit.amount-input").fill("10.00");
  await page.getByTestId("payments.submit.currency-input").fill("EUR");
  await page.getByTestId("payments.submit.debtor-iban-input").fill("DE89370400440532013000");
  await page.getByTestId("payments.submit.creditor-iban-input").fill("FR7630006000011234567890189");
  await page.getByTestId("payments.submit.submit-button").click();
  await page.getByTestId("payments.submit.confirm-dialog.confirm-button").click();
  await expect(page.getByTestId("payments.list.end-to-end-id-link")).toHaveText(e2e);
  const href = await page.getByTestId("payments.list.end-to-end-id-link").getAttribute("href");
  expect(href).toMatch(/^\/payments\/[0-9a-f-]{36}$/);
  await page.evaluate(id => { document.body.dataset.phaseDPaymentId = id!; }, href!.slice("/payments/".length));
});
```

The source-fixed journey identifiers let the evidence client query the resulting payment id inside
PostgreSQL; the successful browser container exposes only a constant success marker via `File`, a
method present in the generated SDK. This makes browser success an explicit graph dependency
without exporting a browser profile, cookie, token, or host artifact.

```ts
// d3b-maker-checker-approval.spec.ts
import {expect,test} from "@playwright/test";
test("D3B maker cannot approve; independent checker can", async ({browser}) => {
  const maker=await browser.newContext(), checker=await browser.newContext();
  const makerPage=await maker.newPage(), checkerPage=await checker.newPage();
  const e2e="D3B-MAKER-CHECKER-0001";
  await login(makerPage,process.env.SMOKE_SUBMITTER_USERNAME!,process.env.SMOKE_SUBMITTER_PASSWORD!);
  // submit same source-shaped five fields as JSON_DIRECT; fixture makes it PENDING_APPROVAL.
  await makerPage.goto("/payments"); /* fill form and confirm exactly as above */
  await expect(makerPage.getByTestId("payments.approvals.queue")).toBeVisible();
  await expect(makerPage.getByTestId(/payments\.approvals\.queue\.approve\./)).toBeDisabled();
  await login(checkerPage,process.env.SMOKE_APPROVER_USERNAME!,process.env.SMOKE_APPROVER_PASSWORD!);
  await expect(checkerPage.getByTestId("payments.approvals.queue")).toBeVisible();
  const row=checkerPage.getByTestId(/payments\.approvals\.queue\.row\./); await expect(row).toContainText("PENDING_APPROVAL");
  await checkerPage.getByTestId(/payments\.approvals\.queue\.approve\./).click();
  await checkerPage.getByTestId("payments.approvals.approve.confirm-button").click();
  await expect(checkerPage.getByTestId("payments.approvals.queue.empty")).toBeVisible();
  await maker.close(); await checker.close();
});
```

```ts
// d3b-payment-detail-lineage.spec.ts
import {expect,test} from "@playwright/test";
test("D3B Payment Detail renders supported JSON_DIRECT lineage", async ({page}) => {
  await login(page,process.env.SMOKE_SUBMITTER_USERNAME!,process.env.SMOKE_SUBMITTER_PASSWORD!);
  // create the independent NOT_REQUIRED source payment exactly as JSON_DIRECT above; follow actual href.
  await page.getByTestId("payments.list.end-to-end-id-link").click();
  await expect(page.getByTestId("payment.detail.page")).toBeVisible();
  await expect(page.getByTestId("payment.detail.end-to-end-id")).toHaveText("D3B-DETAIL-LINEAGE-0001");
  await expect(page.getByTestId("payment.detail.status")).toContainText("RECEIVED");
  await expect(page.getByTestId("payment.detail.timeline.list")).toBeVisible();
  await expect(page.getByTestId("payment.detail.iso-identifiers.row")).toContainText("JSON_DIRECT");
  await page.getByTestId("payment.detail.evidence.trigger").click();
  await expect(page.getByTestId("audit.trail.table")).toBeVisible();
  await expect(page.getByTestId("iso.evidence.messages.table")).toBeVisible();
});
```

Do not assert a payment identifier in the list UI, fields absent from `PaymentDetailResponse`,
a Kafka offset, browser storage, exact timestamp, or a finality state.

## D4 leaves

| Public function / CLI | Controlled fault and service graph | Expected marker/classification | Timeout |
|---|---|---|---:|
| `ResiliencePostgresUnavailable` / `dagger call resilience-postgres-unavailable` | bind backend to an unstarted/no-listener PG alias; finite health client | `PHASE-D EXPECTED POSTGRES_UNAVAILABLE`; non-zero child propagates | 30s |
| `ResilienceKafkaUnavailable` | backend with unreachable Kafka alias and scheduler enabled | `... KAFKA_UNAVAILABLE`; health/error non-success | 30s |
| `ResilienceKeycloakUnavailable` | frontend issuer points to missing alias; readiness cannot succeed | `... KEYCLOAK_UNAVAILABLE` | 30s |
| `ResilienceBackendUnavailable` | frontend BFF uses missing backend alias, request finite | `... BACKEND_UNAVAILABLE` | 30s |
| `ResilienceFrontendUnavailable` | browser/readiness client to missing frontend service | `... FRONTEND_UNAVAILABLE` | 30s |
| `ResilienceBrowserNavigationFailure` | working services, navigate known absent route or invalid origin in isolated client | `... BROWSER_NAVIGATION_FAILED` | Playwright 30s |
| `ResilienceChildNonZero` | `WithExec(["sh","-ec","exit 23"])` beneath marker consumer | no success marker, propagated `CHILD_EXIT_NON_ZERO` | immediate |
| `ResilienceBoundedTimeout` | `WithExec(["sh","-ec","... bounded loop ..."])` missing endpoint | no success marker, `READINESS_TIMEOUT` | 3s |
| `CacheHit` | run a deterministic source leaf twice in same definition | redacted `PHASE-D CACHE-HIT-OBSERVED` after implementation observation | n/a |
| `CacheSourceInvalidation` | exact leaf with a non-sensitive source input change | `... CACHE-SOURCE-INVALIDATED` | n/a |
| `CacheConfigurationInvalidation` | exact leaf with changed nonsecret config | `... CACHE-CONFIG-INVALIDATED` | n/a |
| `CacheSecretInvalidation` | two otherwise-identical secret identities; no value output | `... CACHE-SECRET-DEPENDENCY-OBSERVED` or `UNRESOLVED-SOURCE-CONTRACT` | n/a |

Every D4 leaf uses a fresh graph; Dagger disposal tears down services. Failure artifacts are only
redacted Playwright trace/screenshot/console/network and bounded readiness/health diagnostics.
They must exclude command environments, HTTP authorization headers, cookies, query strings with
codes, realm JSON, and secret values.

## Cache policy

| Input | Policy | Cold / warm target | Invalidation proof |
|---|---|---|---|
| Maven repository | named `debina-maven-jdk25` cache | measure in wave, do not promise | source/pom change reruns Maven layer |
| pnpm store | named existing pnpm cache | measure in wave | lockfile change reruns install layer |
| frontend build | Dagger result cache only; no mutable named `.next` cache | measure | frontend source/config changes rerun build |
| Playwright browser | immutable pinned image path `/ms-playwright` | image pull then reuse | image digest change |
| runtime services, DB/Kafka/Keycloak, sessions/cookies/tokens, test results, generated credentials | never cache | always fresh | absence is inspected in graph code |

## Post-Phase-D architecture hardening (2026-07-23)

The approved local Phase D scope also owns reproducibility and proof quality.
The hardening review added no business capability and changed no frozen
architecture decision:

- the auto-injected Workspace is constructor state, preventing stale dirty-source
  result reuse;
- `.dagger/lock` controls all ten runtime image lookups and frozen mode fails
  closed;
- backend, frontend, Dagger and governance input boundaries are explicit;
- pnpm dependency installation depends only on its manifests;
- Maven, pnpm and Go caches are toolchain-versioned, explicit `SHARED`, and
  covered by an isolated concurrent-writer fixture;
- cache acceptance uses cold/warm/changed traces, not equal digest or duration;
- service alias/DNS, binding regeneration and unexpected runtime log failures
  have focused regressions;
- Engine 0.21.7 remains a dedicated future CLI+Engine upgrade, not an implicit
  Phase D dependency.

Evidence and external-source decisions live in
`docs/ci/DAGGER-ARCHITECTURE-REVIEW-2026-07-23.md`.

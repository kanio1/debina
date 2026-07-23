package main

import "dagger/debina-verification/internal/dagger"

// phaseDCredentials materializes the immutable local development authorities
// as Dagger secrets once per service graph. The migration scripts, application
// configuration, and canonical realm remain their source of truth.
//
// Keep this bundle graph-local: Dagger secrets are intentionally not cached,
// exported, or converted into ordinary environment variables.
type phaseDCredentials struct {
	migrationPassword      *dagger.Secret
	appPassword            *dagger.Secret
	referenceDataPassword  *dagger.Secret
	outboxPassword         *dagger.Secret
	keycloakDBPassword     *dagger.Secret
	keycloakAdminPassword  *dagger.Secret
	webClientSecret        *dagger.Secret
	submitterUsername      *dagger.Secret
	submitterPassword      *dagger.Secret
	approverUsername       *dagger.Secret
	approverPassword       *dagger.Secret
	signaturePassword      *dagger.Secret
	ledgerPassword         *dagger.Secret
	settlementPassword     *dagger.Secret
	grossInstantPassword   *dagger.Secret
	auditAuditorPassword   *dagger.Secret
	approvalExpiryPassword *dagger.Secret
}

func newPhaseDCredentials() phaseDCredentials {
	return phaseDCredentials{
		migrationPassword:      dag.SetSecret("phase-d-migration-password", "dev-only-migration"),
		appPassword:            dag.SetSecret("phase-d-sepa-app-password", "dev-only-app"),
		referenceDataPassword:  dag.SetSecret("phase-d-reference-data-password", "dev-only-reference-data"),
		outboxPassword:         dag.SetSecret("phase-d-outbox-dispatcher-password", "dev-only-outbox-dispatcher"),
		keycloakDBPassword:     dag.SetSecret("phase-d-keycloak-db-password", "dev-only-keycloak-db"),
		keycloakAdminPassword:  dag.SetSecret("phase-d-keycloak-admin-password", "dev-only-admin"),
		webClientSecret:        dag.SetSecret("phase-d-keycloak-web-client-secret", "dev-only-sepa-web-secret"),
		submitterUsername:      dag.SetSecret("phase-d-submitter-username", "submitter"),
		submitterPassword:      dag.SetSecret("phase-d-submitter-password", "dev-only-submitter"),
		approverUsername:       dag.SetSecret("phase-d-approver-username", "approver"),
		approverPassword:       dag.SetSecret("phase-d-approver-password", "dev-only-approver"),
		signaturePassword:      dag.SetSecret("phase-d-signature-password", "dev-only-signature"),
		ledgerPassword:         dag.SetSecret("phase-d-ledger-password", "dev-only-ledger"),
		settlementPassword:     dag.SetSecret("phase-d-settlement-password", "dev-only-settlement"),
		grossInstantPassword:   dag.SetSecret("phase-d-gross-instant-password", "dev-only-gross-instant-executor"),
		auditAuditorPassword:   dag.SetSecret("phase-d-audit-auditor-password", "dev-only-audit-auditor"),
		approvalExpiryPassword: dag.SetSecret("phase-d-approval-expiry-password", "dev-only-approval-expiry"),
	}
}

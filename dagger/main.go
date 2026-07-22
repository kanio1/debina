// Package main implements Debina's local-only Dagger verification module.
package main

import "dagger/debina-verification/internal/dagger"

const (
	goImage         = "golang:1.26.5-bookworm"
	javaImage       = "eclipse-temurin:25-jdk"
	nodeImage       = "node:24.18.0-bookworm"
	postgresImage   = "postgres:18"
	keycloakImage   = "quay.io/keycloak/keycloak:26.6.4"
	playwrightImage = "mcr.microsoft.com/playwright:v1.60.0-noble"
)

// DebinaVerification owns local check composition. Repository commands remain
// the authoritative verification leaves.
type DebinaVerification struct {
}

func New() *DebinaVerification { return &DebinaVerification{} }

// source reads the caller workspace on each check. Dagger's check dispatcher
// invokes annotations directly, so constructor state is intentionally avoided.
func (m *DebinaVerification) source() *dagger.Directory {
	return dag.CurrentWorkspace().Directory("/", dagger.WorkspaceDirectoryOpts{
		Exclude: sourceExcludes,
	})
}

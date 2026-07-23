// Package main implements Debina's local-only Dagger verification module.
package main

import "dagger/debina-verification/internal/dagger"

const (
	alpineImage              = "alpine:3.23.3"
	alpineCompatibilityImage = "alpine:3.22"
	curlImage                = "curlimages/curl:8.16.0"
	goImage                  = "golang:1.26.5-bookworm"
	javaImage                = "eclipse-temurin:25-jdk"
	kafkaImage               = "apache/kafka:4.1.1"
	nodeImage                = "node:24.18.0-bookworm"
	postgresImage            = "postgres:18"
	keycloakImage            = "quay.io/keycloak/keycloak:26.6.4"
	playwrightImage          = "mcr.microsoft.com/playwright:v1.60.0-noble"
)

var runtimeImages = []string{
	alpineImage,
	alpineCompatibilityImage,
	curlImage,
	goImage,
	javaImage,
	kafkaImage,
	nodeImage,
	postgresImage,
	keycloakImage,
	playwrightImage,
}

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

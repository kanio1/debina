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

var sharedCache = dagger.ContainerWithMountedCacheOpts{
	Sharing: dagger.CacheSharingModeShared,
}

// DebinaVerification owns local check composition. Repository commands remain
// the authoritative verification leaves.
type DebinaVerification struct {
	// +private
	Source *dagger.Directory
}

// New captures the auto-injected caller workspace in the module object. The
// resulting Directory ID participates in function cache keys, so dirty source
// changes cannot reuse a result computed for an older workspace snapshot.
func New(workspace *dagger.Workspace) *DebinaVerification {
	return &DebinaVerification{
		Source: workspace.Directory("/", dagger.WorkspaceDirectoryOpts{
			Exclude: sourceExcludes,
		}),
	}
}

func (m *DebinaVerification) source() *dagger.Directory { return m.Source }

// backendWorkspace contains only the Maven wrapper and backend tree. Changes
// to frontend, documentation, planning or the Dagger module cannot invalidate
// backend compilation and test vertices.
func (m *DebinaVerification) backendWorkspace() *dagger.Directory {
	source := m.source()
	return dag.Directory().
		WithFile("mvnw", source.File("mvnw")).
		WithDirectory(".mvn", source.Directory(".mvn")).
		WithDirectory("backend", source.Directory("backend")).
		WithFile("infra/asyncapi/asyncapi.yaml", source.File("infra/asyncapi/asyncapi.yaml"))
}

// frontendWorkspace retains the complete frontend tree, including the
// committed generated GraphQL client, plus the one source-owned backend schema
// consumed by GraphQL codegen. Other backend inputs remain excluded.
func (m *DebinaVerification) frontendWorkspace() *dagger.Directory {
	source := m.source()
	return dag.Directory().
		WithDirectory("frontend", source.Directory("frontend")).
		WithFile(
			"backend/src/main/resources/graphql/schema.graphqls",
			source.File("backend/src/main/resources/graphql/schema.graphqls"),
		)
}

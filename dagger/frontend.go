package main

import "dagger/debina-verification/internal/dagger"

// frontendDependencies isolates dependency resolution from application and
// Playwright source changes. Only the package manifest, workspace definition
// and frozen lockfile participate in the install vertex cache key.
func (m *DebinaVerification) frontendDependencies(container *dagger.Container) *dagger.Container {
	source := m.source()
	return container.
		WithMountedCache("/pnpm/store", dag.CacheVolume("debina-pnpm-node24.18.0-pnpm10.33.0"), sharedCache).
		WithEnvVariable("PNPM_HOME", "/pnpm").
		WithEnvVariable("PNPM_STORE_DIR", "/pnpm/store").
		WithFile("/workspace/frontend/package.json", source.File("frontend/package.json")).
		WithFile("/workspace/frontend/pnpm-lock.yaml", source.File("frontend/pnpm-lock.yaml")).
		WithFile("/workspace/frontend/pnpm-workspace.yaml", source.File("frontend/pnpm-workspace.yaml")).
		WithWorkdir("/workspace/frontend").
		WithExec([]string{"corepack", "enable", "pnpm"}).
		WithExec([]string{"pnpm", "config", "set", "store-dir", "/pnpm/store"}).
		WithExec([]string{"pnpm", "install", "--frozen-lockfile"})
}

func (m *DebinaVerification) frontendFast() *dagger.Container {
	return m.frontendDependencies(dag.Container().From(nodeImage)).
		WithDirectory("/workspace", m.frontendWorkspace()).
		WithExec([]string{"sh", "-c", "cp src/generated/graphql.ts /tmp/graphql.ts && pnpm run codegen:graphql && cmp -s /tmp/graphql.ts src/generated/graphql.ts"}).
		WithExec([]string{"pnpm", "run", "lint"}).
		WithExec([]string{"pnpm", "run", "typecheck"})
}

func (m *DebinaVerification) frontendBuild() *dagger.Container {
	return m.frontendDependencies(dag.Container().From(nodeImage)).
		WithDirectory("/workspace", m.frontendWorkspace()).
		WithExec([]string{"pnpm", "run", "build"})
}

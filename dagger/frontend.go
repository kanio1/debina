package main

import "dagger/debina-verification/internal/dagger"

func (m *DebinaVerification) frontendFast() *dagger.Container {
	return dag.Container().
		From(nodeImage).
		WithMountedCache("/pnpm/store", dag.CacheVolume("debina-pnpm-node24.18.0-pnpm10.33.0")).
		WithEnvVariable("PNPM_HOME", "/pnpm").
		WithEnvVariable("PNPM_STORE_DIR", "/pnpm/store").
		WithDirectory("/workspace", m.source()).
		WithWorkdir("/workspace/frontend").
		WithExec([]string{"corepack", "enable", "pnpm"}).
		WithExec([]string{"pnpm", "config", "set", "store-dir", "/pnpm/store"}).
		WithExec([]string{"pnpm", "install", "--frozen-lockfile"}).
		WithExec([]string{"sh", "-c", "cp src/generated/graphql.ts /tmp/graphql.ts && pnpm run codegen:graphql && cmp -s /tmp/graphql.ts src/generated/graphql.ts"}).
		WithExec([]string{"pnpm", "run", "lint"}).
		WithExec([]string{"pnpm", "run", "typecheck"})
}

func (m *DebinaVerification) frontendBuild() *dagger.Container {
	return dag.Container().
		From(nodeImage).
		WithMountedCache("/pnpm/store", dag.CacheVolume("debina-pnpm-node24.18.0-pnpm10.33.0")).
		WithEnvVariable("PNPM_HOME", "/pnpm").
		WithEnvVariable("PNPM_STORE_DIR", "/pnpm/store").
		WithDirectory("/workspace", m.source()).
		WithWorkdir("/workspace/frontend").
		WithExec([]string{"corepack", "enable", "pnpm"}).
		WithExec([]string{"pnpm", "config", "set", "store-dir", "/pnpm/store"}).
		WithExec([]string{"pnpm", "install", "--frozen-lockfile"}).
		WithExec([]string{"pnpm", "run", "build"})
}

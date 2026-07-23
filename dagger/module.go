package main

import "dagger/debina-verification/internal/dagger"

func (m *DebinaVerification) moduleSelfTest() *dagger.Container {
	cache := dag.CacheVolume("debina-dagger-go-1.26.5")
	module := m.source().Directory("dagger")
	return dag.Container().
		From(goImage).
		WithMountedCache("/go/pkg/mod", cache, sharedCache).
		WithMountedDirectory("/src", module).
		WithWorkdir("/src").
		// The generated binding requires a live Dagger session at package init
		// time. Compile the root package without executing it, and run the pure
		// and command-package tests normally so self-verification stays offline
		// and does not manufacture fake session failures in otherwise green logs.
		WithExec([]string{"go", "test", "-c", "-o", "/tmp/debina-verification.test", "."}).
		WithExec([]string{"go", "test", "./pure", "./cmd/..."}).
		WithExec([]string{"go", "vet", "./..."}).
		WithExec([]string{"sh", "-c", "test -z \"$(gofmt -l .)\""})
}

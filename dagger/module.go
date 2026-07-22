package main

import "dagger/debina-verification/internal/dagger"

func (m *DebinaVerification) moduleSelfTest() *dagger.Container {
	cache := dag.CacheVolume("debina-dagger-go-1.26.5")
	module := m.source().Directory("dagger")
	return dag.Container().
		From(goImage).
		WithMountedCache("/go/pkg/mod", cache).
		WithMountedDirectory("/src", module).
		WithWorkdir("/src").
		WithExec([]string{"go", "test", "./..."}).
		WithExec([]string{"go", "vet", "./..."}).
		WithExec([]string{"sh", "-c", "test -z \"$(gofmt -l .)\""})
}

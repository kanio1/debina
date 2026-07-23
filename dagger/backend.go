package main

import "dagger/debina-verification/internal/dagger"

func (m *DebinaVerification) backendTagged(tag string) *dagger.Container {
	return dag.Container().
		From(javaImage).
		WithMountedCache("/root/.m2/repository", dag.CacheVolume("debina-maven-jdk25"), sharedCache).
		WithDirectory("/workspace", m.backendWorkspace()).
		WithWorkdir("/workspace").
		WithExec([]string{"./mvnw", "-f", "backend", "test", "-Dgroups=" + tag})
}

func (m *DebinaVerification) backendIntegration() *dagger.Container {
	return m.backendTagged("fast")
}

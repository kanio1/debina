package main

import "dagger/debina-verification/internal/dagger"

const fastBackendTests = "ModularityTest,OwnershipArchRulesTest,PaymentNoGodModuleTest,ClockPortEnforcementTest,RuntimeDatasourceOwnershipTest,SchedulerActivationGuardTest"

func (m *DebinaVerification) backendFast() *dagger.Container {
	return dag.Container().
		From(javaImage).
		WithMountedCache("/root/.m2/repository", dag.CacheVolume("debina-maven-jdk25")).
		WithDirectory("/workspace", m.source()).
		WithWorkdir("/workspace").
		WithExec([]string{"./mvnw", "-f", "backend", "-DskipTests", "compile"}).
		WithExec([]string{"./mvnw", "-f", "backend", "test", "-Dtest=" + fastBackendTests})
}

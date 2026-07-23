package main

import (
	"context"
	"fmt"

	"dagger/debina-verification/internal/dagger"
)

const testcontainersSocketPath = "/var/run/docker.sock"

// SocketTransportProbe proves an explicitly supplied runtime socket reaches a
// disposable diagnostic container. It is not a no-argument check.
func (m *DebinaVerification) SocketTransportProbe(ctx context.Context, runtimeSocket *dagger.Socket) (string, error) {
	return dag.Container().
		From(alpineCompatibilityImage).
		WithUnixSocket(testcontainersSocketPath, runtimeSocket).
		WithExec([]string{"sh", "-ec", "apk add --no-cache curl >/dev/null && curl --fail --silent --show-error --unix-socket /var/run/docker.sock http://localhost/_ping"}).
		Stdout(ctx)
}

func (m *DebinaVerification) testcontainersMaven(runtimeSocket *dagger.Socket) *dagger.Container {
	return dag.Container().
		From(javaImage).
		WithMountedCache("/root/.m2/repository", dag.CacheVolume("debina-maven-jdk25"), sharedCache).
		WithDirectory("/workspace", m.backendWorkspace()).
		WithWorkdir("/workspace").
		WithUnixSocket(testcontainersSocketPath, runtimeSocket).
		WithEnvVariable("DOCKER_HOST", "unix:///var/run/docker.sock").
		WithEnvVariable("TESTCONTAINERS_HOST_OVERRIDE", "host.containers.internal")
}

// TestcontainersRepresentative runs an existing PostgreSQL-backed test using
// only an explicitly supplied runtime socket. It is not a no-argument check.
func (m *DebinaVerification) TestcontainersRepresentative(ctx context.Context, runtimeSocket *dagger.Socket) (string, error) {
	return m.testcontainersMaven(runtimeSocket).
		WithExec([]string{"./mvnw", "-f", "backend", "-Dtest=PaymentsRlsTest", "test"}).
		Stdout(ctx)
}

// TestcontainersFinalityPortability runs the timezone-portable finality proof
// using the same explicit runtime-socket contract as the complete regression.
func (m *DebinaVerification) TestcontainersFinalityPortability(ctx context.Context, runtimeSocket *dagger.Socket) (string, error) {
	return m.testcontainersMaven(runtimeSocket).
		WithExec([]string{"./mvnw", "-f", "backend", "-Dtest=SettlementFinalityServiceTest", "test"}).
		Stdout(ctx)
}

// TestcontainersRegression runs the existing complete backend Maven suite.
// The runtime socket is an explicit, required Dagger argument.
func (m *DebinaVerification) TestcontainersRegression(ctx context.Context, runtimeSocket *dagger.Socket) (string, error) {
	return m.testcontainersMaven(runtimeSocket).
		WithExec([]string{"./mvnw", "-f", "backend", "test"}).
		Stdout(ctx)
}

// RuntimeReachabilityProbe checks Podman's documented host address from a
// disposable container. It deliberately receives no runtime socket.
func (m *DebinaVerification) RuntimeReachabilityProbe(ctx context.Context, port int) (string, error) {
	command := fmt.Sprintf("apk add --no-cache netcat-openbsd >/dev/null && getent hosts host.containers.internal && nc -z -v -w 3 host.containers.internal %d", port)
	return dag.Container().
		From(alpineCompatibilityImage).
		WithExec([]string{"sh", "-ec", command}).
		Stdout(ctx)
}

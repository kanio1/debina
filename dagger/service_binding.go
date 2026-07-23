package main

import (
	"context"

	"dagger/debina-verification/internal/dagger"
)

// ServiceBindingDNS proves that WithServiceBinding preserves the requested
// alias through a subsequent WithExec and that the client resolves and reaches
// the lazy service without a host port or manual Start/Stop lifecycle.
func (m *DebinaVerification) ServiceBindingDNS(ctx context.Context) (string, error) {
	const alias = "service-dns"
	service := dag.Container().
		From(alpineImage).
		WithWorkdir("/srv").
		WithExposedPort(8080).
		AsService(dagger.ContainerAsServiceOpts{Args: []string{
			"sh",
			"-ec",
			`while true; do printf 'HTTP/1.1 200 OK\r\nContent-Length: 35\r\nConnection: close\r\n\r\nDEBINA SERVICE-BINDING DNS VERIFIED' | nc -l -p 8080; done`,
		}})

	return dag.Container().
		From(alpineImage).
		WithServiceBinding(alias, service).
		WithExec([]string{"sh", "-ec", "grep -F '" + alias + "' /etc/hosts >/dev/null; test \"$(wget -qO- http://" + alias + ":8080/)\" = 'DEBINA SERVICE-BINDING DNS VERIFIED'; echo 'PHASE-D SERVICE-BINDING DNS VERIFIED'"}).
		Stdout(ctx)
}

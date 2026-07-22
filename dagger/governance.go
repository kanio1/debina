package main

import "dagger/debina-verification/internal/dagger"

func (m *DebinaVerification) governance() *dagger.Container {
	return dag.Container().
		From(goImage).
		WithMountedDirectory("/src", m.source()).
		WithWorkdir("/src").
		WithExec([]string{"bash", "tools/agent-config/validate-enterprise-governance.sh"})
}

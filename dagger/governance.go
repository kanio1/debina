package main

import "dagger/debina-verification/internal/dagger"

func (m *DebinaVerification) governance() *dagger.Container {
	// The authoritative enterprise governance suite is Python. The Go toolchain
	// image provides python3 but not PyYAML; install the distro module only for
	// this finite validation container (no application dependency change).
	return dag.Container().
		From(goImage).
		WithMountedDirectory("/src", m.source()).
		WithWorkdir("/src").
		WithExec([]string{"bash", "-ec", `
set -eu
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq python3-yaml >/dev/null
bash tools/agent-config/validate-enterprise-governance.sh
`})
}

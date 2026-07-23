package main

import (
	"context"
	"fmt"
)

// SourceBoundaryDigest exposes the content digest of one declared graph input
// boundary for controlled invalidation proofs. It does not export source
// contents or materialize a build.
func (m *DebinaVerification) SourceBoundaryDigest(ctx context.Context, boundary string) (string, error) {
	var directory interface {
		Digest(context.Context) (string, error)
	}

	switch boundary {
	case "backend":
		directory = m.backendWorkspace()
	case "frontend":
		directory = m.frontendWorkspace()
	case "dagger":
		directory = m.source().Directory("dagger")
	case "governance":
		directory = m.source()
	default:
		return "", fmt.Errorf("unknown source boundary %q: expected backend, frontend, dagger, or governance", boundary)
	}
	return directory.Digest(ctx)
}

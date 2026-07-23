package main

import (
	"context"
	"crypto/sha256"
	"fmt"

	"dagger/debina-verification/internal/dagger"
)

// CacheVolumeStress proves two concurrent writers and a later reader against
// a dedicated SHARED cache namespace. Reset removes only this fixture's files
// from that namespace; it never clears a user or tool cache.
func (m *DebinaVerification) CacheVolumeStress(
	ctx context.Context,
	namespace string,
	// +optional
	reset bool,
) (string, error) {
	sum := sha256.Sum256([]byte(namespace))
	cache := dag.CacheVolume(fmt.Sprintf("debina-sharing-stress-v1-%x", sum[:8]))

	if reset {
		if _, err := dag.Container().
			From(alpineImage).
			WithMountedCache("/cache", cache, sharedCache).
			WithExec([]string{"sh", "-ec", "rm -f /cache/debina-writer-a /cache/debina-writer-b"}).
			Sync(ctx); err != nil {
			return "", fmt.Errorf("reset dedicated cache fixture: %w", err)
		}
	}

	writer := func(name string) *dagger.Container {
		return dag.Container().
			From(alpineImage).
			WithMountedCache("/cache", cache, sharedCache).
			WithExec([]string{"sh", "-ec", "printf '%s\\n' " + name + " > /cache/debina-writer-" + name})
	}
	if err := runChecks(ctx, []namedCheck{
		containerCheck("cache-writer-a", writer("a")),
		containerCheck("cache-writer-b", writer("b")),
	}); err != nil {
		return "", err
	}

	return dag.Container().
		From(alpineImage).
		WithMountedCache("/cache", cache, sharedCache).
		WithExec([]string{"sh", "-ec", "grep -x a /cache/debina-writer-a >/dev/null; grep -x b /cache/debina-writer-b >/dev/null; echo 'SHARED-CACHE-CONCURRENT-WRITERS-PROVEN'"}).
		Stdout(ctx)
}

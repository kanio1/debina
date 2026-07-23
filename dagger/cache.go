package main

import (
	"context"
	"fmt"
	"strings"

	"dagger/debina-verification/internal/dagger"
)

func (m *DebinaVerification) cacheProbeContainer(sourceInput, configuration string) *dagger.Container {
	return dag.Container().
		From("alpine:3.23.3").
		WithFile("/probe/go.mod", m.source().File("dagger/go.mod")).
		WithNewFile("/probe/source-input", sourceInput).
		WithNewFile("/probe/configuration", configuration).
		WithExec([]string{"sh", "-ec", `
set -eu
digest="$(sha256sum /probe/go.mod /probe/source-input /probe/configuration | sha256sum | cut -d ' ' -f 1)"
printf 'PHASE-D CACHE-PROBE %s\n' "$digest"
`})
}

// CacheProbe exposes a deterministic vertex whose cache key includes the
// pinned image, dagger/go.mod, source-input and configuration arguments.
func (m *DebinaVerification) CacheProbe(ctx context.Context, sourceInput, configuration string) (string, error) {
	return m.cacheProbeContainer(sourceInput, configuration).Stdout(ctx)
}

// CacheReuse verifies stable output for two identical deterministic vertices.
// The CLI progress trace is the runtime authority for the second vertex's
// explicit CACHED state.
func (m *DebinaVerification) CacheReuse(ctx context.Context) (string, error) {
	first, err := m.cacheProbeContainer("baseline-source", "baseline-config").Stdout(ctx)
	if err != nil {
		return "", fmt.Errorf("cold cache probe failed: %w", err)
	}
	second, err := m.cacheProbeContainer("baseline-source", "baseline-config").Stdout(ctx)
	if err != nil {
		return "", fmt.Errorf("warm cache probe failed: %w", err)
	}
	if strings.TrimSpace(first) != strings.TrimSpace(second) {
		return "", fmt.Errorf("CACHE_CONTRACT: identical inputs produced different outputs")
	}
	return "PHASE-D CACHE-REUSE OUTPUT VERIFIED", nil
}

// CacheInvalidation verifies that changing one relevant source/config input
// changes only the deterministic probe result; the progress trace shows reuse
// of independent image and source-transfer vertices.
func (m *DebinaVerification) CacheInvalidation(ctx context.Context) (string, error) {
	baseline, err := m.cacheProbeContainer("baseline-source", "baseline-config").Stdout(ctx)
	if err != nil {
		return "", fmt.Errorf("baseline cache probe failed: %w", err)
	}
	sourceChanged, err := m.cacheProbeContainer("changed-source", "baseline-config").Stdout(ctx)
	if err != nil {
		return "", fmt.Errorf("source invalidation probe failed: %w", err)
	}
	configChanged, err := m.cacheProbeContainer("baseline-source", "changed-config").Stdout(ctx)
	if err != nil {
		return "", fmt.Errorf("configuration invalidation probe failed: %w", err)
	}
	baseline = strings.TrimSpace(baseline)
	sourceChanged = strings.TrimSpace(sourceChanged)
	configChanged = strings.TrimSpace(configChanged)
	if baseline == sourceChanged || baseline == configChanged || sourceChanged == configChanged {
		return "", fmt.Errorf("CACHE_CONTRACT: relevant input change did not produce an independent result")
	}
	return "PHASE-D CACHE-SOURCE-INVALIDATED\nPHASE-D CACHE-CONFIG-INVALIDATED", nil
}

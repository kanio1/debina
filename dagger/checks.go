package main

import (
	"context"
	"fmt"

	"dagger/debina-verification/internal/dagger"
)

// Fast runs high-signal checks that do not provision the full runtime.
// +check
func (m *DebinaVerification) Fast(ctx context.Context) error {
	checks := []namedCheck{
		{name: "governance", container: m.governance()},
		{name: "module", container: m.moduleSelfTest()},
	}
	return runChecks(ctx, checks)
}

type namedCheck struct {
	name      string
	container *dagger.Container
}

func runChecks(ctx context.Context, checks []namedCheck) error {
	results := make(chan error, len(checks))
	for _, check := range checks {
		check := check
		go func() {
			_, err := check.container.Sync(ctx)
			if err != nil {
				results <- fmt.Errorf("%s: %w", check.name, err)
				return
			}
			results <- nil
		}()
	}
	var failures []error
	for range checks {
		if err := <-results; err != nil {
			failures = append(failures, err)
		}
	}
	return joinErrors(failures)
}

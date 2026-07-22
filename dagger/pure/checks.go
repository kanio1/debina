// Package pure contains Dagger-agnostic check composition so it can be tested
// without a generated SDK session.
package pure

import (
	"context"
	"errors"
	"fmt"
)

type NamedCheck struct {
	Name string
	Run  func(context.Context) error
}

func RunChecks(ctx context.Context, checks []NamedCheck) error {
	results := make(chan error, len(checks))
	for _, check := range checks {
		check := check
		go func() {
			if err := check.Run(ctx); err != nil {
				results <- fmt.Errorf("%s: %w", check.Name, err)
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
	return errors.Join(failures...)
}

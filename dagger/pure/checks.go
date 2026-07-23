// Package pure contains Dagger-agnostic check composition so it can be tested
// without a generated SDK session.
package pure

import (
	"context"
	"errors"
	"fmt"
	"time"
)

type NamedCheck struct {
	Name    string
	Run     func(context.Context) error
	Timeout time.Duration
}

func RunChecks(ctx context.Context, checks []NamedCheck) error {
	if err := validateChecks(checks); err != nil {
		return err
	}
	results := make(chan error, len(checks))
	for _, check := range checks {
		check := check
		go func() {
			if err := runNamedCheck(ctx, check); err != nil {
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

// RunChecksSequential preserves explicit ordering for resource-heavy browser
// and service graphs while retaining named failure propagation.
func RunChecksSequential(ctx context.Context, checks []NamedCheck) error {
	if err := validateChecks(checks); err != nil {
		return err
	}
	for _, check := range checks {
		if err := runNamedCheck(ctx, check); err != nil {
			return fmt.Errorf("%s: %w", check.Name, err)
		}
	}
	return nil
}

func validateChecks(checks []NamedCheck) error {
	seen := make(map[string]struct{}, len(checks))
	for _, check := range checks {
		if check.Name == "" {
			return errors.New("check name must not be empty")
		}
		if check.Run == nil {
			return fmt.Errorf("%s: check runner must not be nil", check.Name)
		}
		if _, duplicate := seen[check.Name]; duplicate {
			return fmt.Errorf("%s: duplicate check classification", check.Name)
		}
		seen[check.Name] = struct{}{}
	}
	return nil
}

func runNamedCheck(ctx context.Context, check NamedCheck) error {
	if check.Timeout <= 0 {
		return check.Run(ctx)
	}
	childCtx, cancel := context.WithTimeout(ctx, check.Timeout)
	defer cancel()
	return check.Run(childCtx)
}

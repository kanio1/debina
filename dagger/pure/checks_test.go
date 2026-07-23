package pure

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"
)

func TestRunChecksReturnsNamedFailures(t *testing.T) {
	err := RunChecks(context.Background(), []NamedCheck{
		{Name: "passes", Run: func(context.Context) error { return nil }},
		{Name: "database", Run: func(context.Context) error { return errors.New("unavailable") }},
		{Name: "kafka", Run: func(context.Context) error { return errors.New("timed out") }},
	})
	if err == nil {
		t.Fatal("expected composed failures")
	}
	for _, want := range []string{"database: unavailable", "kafka: timed out"} {
		if !strings.Contains(err.Error(), want) {
			t.Fatalf("failure %q was not preserved in %q", want, err)
		}
	}
}

func TestRunChecksSucceedsWhenEveryLeafSucceeds(t *testing.T) {
	err := RunChecks(context.Background(), []NamedCheck{
		{Name: "postgres", Run: func(context.Context) error { return nil }},
		{Name: "kafka", Run: func(context.Context) error { return nil }},
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
}

func TestRunChecksSequentialStopsAtFirstFailure(t *testing.T) {
	var order []string
	err := RunChecksSequential(context.Background(), []NamedCheck{
		{Name: "first", Run: func(context.Context) error {
			order = append(order, "first")
			return nil
		}},
		{Name: "failure", Run: func(context.Context) error {
			order = append(order, "failure")
			return errors.New("unexpected child")
		}},
		{Name: "must-not-run", Run: func(context.Context) error {
			order = append(order, "must-not-run")
			return nil
		}},
	})
	if err == nil || !strings.Contains(err.Error(), "failure: unexpected child") {
		t.Fatalf("expected named child propagation, got %v", err)
	}
	if strings.Join(order, ",") != "first,failure" {
		t.Fatalf("unexpected execution order: %v", order)
	}
}

func TestRunChecksSequentialAppliesFiniteBudget(t *testing.T) {
	err := RunChecksSequential(context.Background(), []NamedCheck{{
		Name:    "bounded",
		Timeout: time.Millisecond,
		Run: func(ctx context.Context) error {
			<-ctx.Done()
			return ctx.Err()
		},
	}})
	if err == nil || !strings.Contains(err.Error(), "context deadline exceeded") {
		t.Fatalf("expected finite timeout propagation, got %v", err)
	}
}

func TestRunChecksRejectsDuplicateClassification(t *testing.T) {
	runner := func(context.Context) error { return nil }
	for name, execute := range map[string]func(context.Context, []NamedCheck) error{
		"parallel":   RunChecks,
		"sequential": RunChecksSequential,
	} {
		t.Run(name, func(t *testing.T) {
			err := execute(context.Background(), []NamedCheck{
				{Name: "same-leaf", Run: runner},
				{Name: "same-leaf", Run: runner},
			})
			if err == nil || !strings.Contains(err.Error(), "duplicate check classification") {
				t.Fatalf("expected duplicate classification rejection, got %v", err)
			}
		})
	}
}

func TestRunChecksRejectsIncompleteClassification(t *testing.T) {
	for _, checks := range [][]NamedCheck{
		{{Name: "", Run: func(context.Context) error { return nil }}},
		{{Name: "missing-runner"}},
	} {
		if err := RunChecksSequential(context.Background(), checks); err == nil {
			t.Fatalf("expected invalid classification rejection for %+v", checks)
		}
	}
}

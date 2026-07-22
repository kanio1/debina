package pure

import (
	"context"
	"errors"
	"strings"
	"testing"
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

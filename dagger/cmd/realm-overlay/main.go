package main

import (
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"

	"dagger/debina-verification/pure"
)

func main() {
	if err := run(os.Args[1:], os.Stdout); err != nil {
		fail(err.Error())
	}
}

func run(args []string, stdout io.Writer) error {
	flags := flag.NewFlagSet("realm-overlay", flag.ContinueOnError)
	flags.SetOutput(io.Discard)
	input := flags.String("input", "", "canonical realm JSON")
	output := flags.String("output", "", "verified derived realm JSON")
	marker := flags.String("marker", "", "verification marker")
	callback := flags.String("callback", "", "approved callback")
	origin := flags.String("origin", "", "approved origin")
	if err := flags.Parse(args); err != nil {
		return err
	}
	if *input == "" || *output == "" || *marker == "" {
		return fmt.Errorf("input, output and marker are required")
	}
	canonical, err := os.ReadFile(*input)
	if err != nil {
		return err
	}
	derived, err := pure.DeriveRealmOverlay(canonical, *callback, *origin)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(*output), 0755); err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(*marker), 0755); err != nil {
		return err
	}
	if err := os.WriteFile(*output, derived, 0644); err != nil {
		return err
	}
	if err := os.WriteFile(*marker, []byte(pure.OverlaySuccessMarker+"\n"), 0644); err != nil {
		return err
	}
	_, err = fmt.Fprintln(stdout, pure.OverlaySuccessMarker)
	return err
}

func fail(message string) { fmt.Fprintln(os.Stderr, "realm overlay failed:", message); os.Exit(1) }

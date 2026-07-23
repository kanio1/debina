package main

import (
	"bufio"
	"fmt"
	"os"
	"regexp"
	"strings"
)

var (
	errorPattern = regexp.MustCompile(`\b(?:ERROR|FATAL)\b`)
)

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "usage: log-health TRACE...")
		os.Exit(2)
	}
	for _, path := range os.Args[1:] {
		if err := scan(path); err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
	}
	fmt.Println("DAGGER-LOG-HEALTH-PROVEN")
}

func scan(path string) error {
	file, err := os.Open(path)
	if err != nil {
		return fmt.Errorf("open %s: %w", path, err)
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	scanner.Buffer(make([]byte, 64*1024), 4*1024*1024)
	lineNumber := 0
	for scanner.Scan() {
		lineNumber++
		line := scanner.Text()
		lower := strings.ToLower(line)
		if strings.Contains(line, "PHASE-D EXPECTED ") {
			continue
		}
		if strings.Contains(line, "NullPointerException") ||
			strings.Contains(lower, "uncaught exception") ||
			strings.Contains(lower, "fatal startup") ||
			strings.Contains(line, "Exception in thread") ||
			strings.Contains(line, "panic:") {
			return fmt.Errorf("%s:%d unexpected exception/panic: %s", path, lineNumber, line)
		}
		// Dagger's own structural span state can be ERROR for an explicitly
		// expected ReturnTypeFailure. Runtime log payload follows " | " and is
		// the surface on which application ERROR/FATAL entries are asserted.
		if payloadIndex := strings.Index(line, " | "); payloadIndex >= 0 &&
			errorPattern.MatchString(line[payloadIndex+3:]) {
			if !expectedKeycloakSchemaBootstrap(line) {
				return fmt.Errorf("%s:%d unexpected runtime ERROR/FATAL: %s", path, lineNumber, line)
			}
		}
	}
	if err := scanner.Err(); err != nil {
		return fmt.Errorf("scan %s: %w", path, err)
	}
	return nil
}

func expectedKeycloakSchemaBootstrap(line string) bool {
	return strings.Contains(line, `ERROR:  relation "migration_model" does not exist`) ||
		strings.Contains(line, `ERROR:  relation "public.databasechangeloglock" does not exist`)
}

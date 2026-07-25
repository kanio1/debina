package main

import (
	"context"
	"fmt"
)

// SmokeSignedPain001 proves the E1 accepted-path browser journey against a
// fresh composed stack: Keycloak login → BFF signed pain.001 upload →
// deterministic created outcome → payment detail. It is separately callable
// and is not part of the ADR-N16 capped smoke-suite.
//
// proofNonce, when set, isolates ephemeral Postgres/Keycloak/Kafka instances so
// a reliability rerun cannot reuse a prior service graph or Playwright result.
// Isolation is the instance suffix passed to paymentSmokeRuntime; dependency
// cache volume names must not incorporate the nonce.
func (m *DebinaVerification) SmokeSignedPain001(ctx context.Context,
	// +optional
	proofNonce string,
) (string, error) {
	instance := "signed-pain001"
	if proofNonce != "" {
		instance = fmt.Sprintf("signed-pain001-%s", proofNonce)
	}
	runtime := m.paymentSmokeRuntime(instance)
	browser := m.paymentPlaywright(
		runtime,
		"pnpm run test:smoke:e1-pain001",
	)
	return browser.Stdout(ctx)
}

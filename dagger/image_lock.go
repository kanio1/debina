package main

import "context"

// ImageLockProbe resolves every explicitly declared runtime image through a
// harmless finite exec. Run it with --lock=live when intentionally creating or
// refreshing .dagger/lock, and with --lock=frozen to prove completeness. The
// non-sensitive proof nonce forces a fresh function evaluation without
// changing the image lookup inputs recorded in the lockfile.
func (m *DebinaVerification) ImageLockProbe(ctx context.Context, proofNonce string) error {
	checks := make([]namedCheck, 0, len(runtimeImages))
	for _, image := range runtimeImages {
		checks = append(checks, containerCheck(image, dag.Container().
			From(image).
			WithEnvVariable("DEBINA_IMAGE_LOCK_PROBE", proofNonce).
			WithExec([]string{"true"})))
	}
	return runChecks(ctx, checks)
}

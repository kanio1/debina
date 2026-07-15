package com.sepanexus.signature;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Story 31.4 (sepa-nexus-signature-module-blueprint.md §8): lookup is always {@code (who, purpose,
 * as_of)} — the one ACTIVE key valid at {@code as_of}. There is no ambient "current key";
 * determinism requires an explicit {@code as_of} (the platform-wide {@code ClockPort} discipline).
 * An expired, future, revoked, or unknown key all resolve to {@link Optional#empty()} — never a
 * soft warning, never a silently-accepted signature.
 */
public interface KeyRegistryPort {

    UUID register(SignatureKeyRegistration registration);

    Optional<SignatureKeyView> lookup(UUID participantId, KeyPurpose purpose, Instant asOf);
}

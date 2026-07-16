package com.sepanexus.signature;

import java.time.Instant;
import java.util.UUID;

/**
 * Everything {@link SignatureVerificationPort#verify} needs, and nothing more — deliberately excludes any
 * parsed document, payment entity, status, or HTTP artifact (sepa-nexus-signature-module-blueprint.md
 * §2 "must not"). {@code rawMessageId} must already reference an archived row (archive-before-verify,
 * G1) so every verdict — including a failed one — can bind to the exact bytes it covers.
 * {@code signatureRequired} is channel policy decided by the caller (e.g. {@code ingress}), never
 * inferred inside this module from a message-type string.
 */
public record SignatureVerificationRequest(UUID rawMessageId, byte[] rawBytes, byte[] signatureBytes,
        UUID declaredSignerId, String algo, String channel, boolean signatureRequired, Instant asOf) {
}

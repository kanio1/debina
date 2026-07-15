package com.sepanexus.signature;

import java.util.UUID;

/**
 * Output of {@link SignaturePort#sign} — {@code egress} attaches and delivers this; {@code
 * signature} never touches transport (sepa-nexus-signature-module-blueprint.md §7).
 */
public record DetachedSignature(byte[] signatureBytes, UUID keyId, String algo) {
}

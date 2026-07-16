package com.sepanexus.signature;

/**
 * (sepa-nexus-signature-module-blueprint.md §4/§10 line 61): {@code egress} calls this only when a
 * resolved egress profile snapshot has {@code signing_required = true}. Split out of the former
 * combined {@code SignaturePort} (EPIC-31 Story 31.3A). Deliberately minimal — no dependency on
 * {@code egress}, transport, or outbound-message types; {@code egress} attaches and delivers the
 * returned {@link DetachedSignature}, {@code signature} never touches transport (§7).
 *
 * <p>{@code signingKeyRef} is the textual {@link java.util.UUID} of the {@code
 * signature.signature_keys} row to sign with — the caller names which key, {@code signature}
 * resolves its own record (purpose, validity, algorithm, private material) rather than trusting
 * anything about the key's material from the caller.
 */
public interface SignatureSigningPort {

    DetachedSignature sign(byte[] artifactBytes, String signingKeyRef);
}

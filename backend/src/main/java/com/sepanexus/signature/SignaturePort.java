package com.sepanexus.signature;

/**
 * (sepa-nexus-signature-module-blueprint.md §4): {@code ingress} calls {@code verify} before any
 * ISO XML parsing on signed channels (verify-before-parse, G1); {@code egress} calls {@code sign}
 * only when a resolved profile requires it. Story 31.2 implements {@code verify}; {@code sign}
 * remains unimplemented until Story 31.3 (egress signing) — wiring it now would be inventing
 * architecture ahead of that story.
 */
public interface SignaturePort {

    Verdict verify(SignatureVerificationRequest request);

    DetachedSignature sign(byte[] artifactBytes, String signingKeyRef);
}

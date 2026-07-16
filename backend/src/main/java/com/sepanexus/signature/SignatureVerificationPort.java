package com.sepanexus.signature;

/**
 * (sepa-nexus-signature-module-blueprint.md §4/§10 line 60): {@code ingress} calls this before any
 * ISO XML parsing on signed channels (verify-before-parse, G1). Split out of the former combined
 * {@code SignaturePort} (EPIC-31 Story 31.3A) once the source blueprint's own port table turned out
 * to already name {@code SignatureVerificationPort}/{@code SignatureSigningPort} as two distinct
 * ports, not one — {@code ingress} never needed {@code sign()}, so the split is a pure separation
 * of already-independent responsibilities, not new scope.
 */
public interface SignatureVerificationPort {

    Verdict verify(SignatureVerificationRequest request);
}

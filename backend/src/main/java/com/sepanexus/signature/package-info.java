/**
 * Signature module (EPIC-31 Story 31.1, sepa-nexus-signature-module-blueprint.md §2/§3): a
 * security boundary that proves or produces a cryptographic signature over raw bytes and records
 * the verdict as evidence. It never parses ISO, never decides payment status or finality, and
 * never writes to any other module's schema. {@link com.sepanexus.signature.SignatureVerificationPort}
 * (Story 31.2) and {@link com.sepanexus.signature.KeyRegistryPort} (Story 31.4) are implemented;
 * {@link com.sepanexus.signature.SignatureSigningPort} (Story 31.3A) is the standalone signing
 * capability — its invocation from {@code egress}'s renderer is a separate, later story (EPIC-43
 * Story 43.2). Allowed to depend on {@code shared} (EPIC-22 Story 22.3 — {@code ClockPort}, used by
 * the Ed25519 signer for a deterministic {@code created_at}) — extend this list further only as a
 * real port this module needs is introduced. Internals ({@code .internal}) are not part of the
 * module's public API — only the ports and value types in this root package are.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared"})
package com.sepanexus.signature;

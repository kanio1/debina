/**
 * Signature module (EPIC-31 Story 31.1, sepa-nexus-signature-module-blueprint.md §2/§3): a
 * security boundary that proves or produces a cryptographic signature over raw bytes and records
 * the verdict as evidence. It never parses ISO, never decides payment status or finality, and
 * never writes to any other module's schema. No allowed dependencies yet — {@code SignaturePort}
 * verification/signing logic (Story 31.2/31.3) and its wiring into {@code ingress}/{@code egress}
 * land in later stories; {@link com.sepanexus.signature.KeyRegistryPort} (Story 31.4) is the only
 * fully implemented capability so far. Internals ({@code .internal}) are not part of the module's
 * public API — only the ports and value types in this root package are.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package com.sepanexus.signature;

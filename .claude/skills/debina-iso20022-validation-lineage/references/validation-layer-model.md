# Validation layer model

Keep every layer independently evidenced; success at one layer proves nothing about another.

1. Transport framing: channel, bytes, limits, and framing facts.
2. Secure XML parsing: DTD/external entity prevention and parser/resource limits.
3. Signature verification: raw-evidence signature verdict and signer/trust evidence when required.
4. XSD/schema validation: exact namespace and exact selected schema/version.
5. EPC Implementation Guideline validation: only rules backed by the applicable public source.
6. CSM-specific profile/TVS validation: only the selected profile/TVS and participant material can prove this.
7. Business semantic validation: project-owned domain rules, separately sourced.
8. Authorization: actor/channel authority, separately decided and tested.
9. Message lineage and correlation: identifiers and relations, never a business-status decision.
10. Duplicate/replay handling: idempotency and conflict outcome, not a parser result.
11. Raw evidence archival: immutable original bytes and integrity metadata, not a validation verdict.

When signed inbound evidence is required by an accepted source, archive bytes and verify its signature before parse/deserialization. Never parse first and retroactively call the result verified.

# Security, channel and signature review — E1

Review state: `NOT_REVIEWED`

Reviewer role: application security, Keycloak and BFF security architects

Approval evidence: `PENDING_DATED_HUMAN_RECORD`

## Exact review scope

Review the E1 REST plus BFF upload channel, authentication/authorization,
tenant resolution, detached signature profile, trust lifecycle, evidence
visibility and telemetry for `UC-SCT-002`.

## Documents and sections inspected

- EPC132-08 C2PSP scope and message profile; no REST, BFF, HTTP detached
  signature or Ed25519 prescription was found in the reviewed IG.
- Current `PaymentController.submitPain001`,
  `SignedChannelIngestionPipeline`, `SignatureVerificationPort`, signature
  schema/migrations and tests (implementation evidence only).
- Keycloak/security blueprints, ADR-N17 (GraphQL query-only), BFF route skill,
  decision register and mapping rows 27–28.

## Claims being approved

- REST/BFF channel shape is under review; `DEBINA-E1-DETACHED-ED25519-V1` is a
  candidate detached Ed25519 cryptographic/evidence profile, not an EPC
  requirement; normative acceptance remains `DECISION_BLOCKED`.
- Exact request bytes are archived and hashed before verification/parsing.
- Tenant comes only from a validated token/session claim; the payload cannot
  select another tenant.
- BFF uses server session, fixed destination, same-origin/CSRF protection and
  never exposes a bearer token.
- XML, IBANs, names, addresses, remittance, signature bytes and key material
  never enter logs/metrics/traces.

## Project decisions proposed

Review the candidate `VERSIONED_DETACHED_ED25519_PROFILE`
(`DEBINA-E1-DETACHED-ED25519-V1`) cryptographic and evidence profile. Current
`PaymentController` headers (`X-Signer-Id`, base64 `X-Signature`,
`X-Signature-Algo`) and `SignedChannelIngestionPipeline` behavior are
implementation evidence only, not normative authority. Define signer authority,
active/expiry/rotation/revocation semantics and stable
missing/malformed/invalid/untrusted outcomes. Normative acceptance remains `DECISION_BLOCKED` pending dated human `SECURITY_REVIEW` and an accepted
dedicated project security/evidence decision or ADR.

## Open questions

- Is signer identity a participant, tenant key, user key or managed channel
  credential?
- What trust and revocation source is authoritative, and how are overlaps
  handled during rotation?
- Are signature bytes allowed through browser/BFF, or must the browser upload
  a pre-signed envelope?
- What rate/replay limits and anti-malware boundary apply?
- Which roles may read raw payload/signature/certificate lineage?

## Rejected alternatives

Custom cryptography, algorithm chosen from untrusted input without allowlist,
private-key upload, frontend authorization, tenant header override, raw
evidence in GraphQL/logs.

## Affected use case, slices and stories

`UC-SCT-002`; `UCS-SCT-002-A`, `UCS-SCT-002-B`; `EPIC-31/31.2`,
`EPIC-19/19.2`, `EPIC-19/19.4`, `EPIC-26/26.4`, proposed `EPIC-24/24.10`.

## Council recommendations (AI_DRAFT)

Council date: 2026-07-24. Review state remains `NOT_REVIEWED`.

- Confirm `REST_PLUS_BFF_UPLOAD` with `application/xml`, `payment_submitter`,
  Idempotency-Key (one opaque key identifies one logical tenant-scoped submission;
  transport retries of the same logical operation reuse the same key; same key plus
  same payload identity/hash returns the original deterministic outcome; same key
  plus materially different payload returns a deterministic conflict; key ownership,
  TTL and retention remain unresolved SECURITY/DATABASE decisions), CSRF/same-origin,
  no browser bearer tokens.
- Recommend `VERSIONED_DETACHED_ED25519_PROFILE` (`DEBINA-E1-DETACHED-ED25519-V1`)
  as candidate cryptographic/evidence profile; normative acceptance remains `DECISION_BLOCKED` pending dated `SECURITY_REVIEW` and accepted
  security/evidence decision or ADR; tenant-scoped signer/key registry is a
  candidate direction; current header/code shape is implementation evidence only;
  signer identifier transport, representation, key binding, rotation and
  revocation remain `DECISION_BLOCKED`.
- Rate/replay limits deferred pending owner-approved experiments.

## Blocking consequences

Channel, signature, limits and evidence visibility remain `DECISION_BLOCKED`.
No implementation or canonical migration is authorised.

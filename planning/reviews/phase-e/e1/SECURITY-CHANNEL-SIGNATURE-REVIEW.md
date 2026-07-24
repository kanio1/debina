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
  schema/migrations and tests.
- Keycloak/security blueprints, ADR-N2/N8/N10/N17, backend/frontend
  instructions, decision register and mapping rows 27–28.

## Claims being approved

- REST/BFF and `DEBINA-E1-DETACHED-ED25519-V1` are project policy only.
- Exact request bytes are archived and hashed before verification/parsing.
- Tenant comes only from a validated token/session claim; the payload cannot
  select another tenant.
- BFF uses server session, fixed destination, same-origin/CSRF protection and
  never exposes a bearer token.
- XML, IBANs, names, addresses, remittance, signature bytes and key material
  never enter logs/metrics/traces.

## Project decisions proposed

Approve or change the versioned current headers (`X-Signer-Id`,
base64 `X-Signature`, explicit `X-Signature-Algo`) and Ed25519 allowlist;
define signer authority, active/expiry/rotation/revocation semantics and stable
missing/malformed/invalid/untrusted outcomes.

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

## Blocking consequences

Channel, signature, limits and evidence visibility remain `DECISION_BLOCKED`.
No implementation or canonical migration is authorised.

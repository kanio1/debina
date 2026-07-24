# E1 owner decision packet

Status: `AI_DRAFT`

Review state: `NOT_REVIEWED`

Authorship: `AI_DRAFT_NOT_HUMAN_REVIEWED`

Prepared: 2026-07-24

Completion markers: `DEBINA-E1-REVIEW-COUNCIL-COMPLETE`, `E1-OWNER-DECISION-PACK-READY`,
`HUMAN-OWNER-DECISION-REQUIRED`, `IMPLEMENTATION-NOT-AUTHORIZED`

Canonical migration: `NOT_ALLOWED`

Production implementation: `NOT_AUTHORIZED`

## Scope

Human owner decisions for `UC-SCT-002` slices `UCS-SCT-002-A` and
`UCS-SCT-002-B`. This packet consolidates council recommendations from
`E1-RECOMMENDATIONS.yaml`; it does not constitute human approval.

## Decision table

| Decision | Recommended option | Alternatives | Evidence | Risks | Blocking consequences | Owner choice |
| -------- | ------------------ | ------------ | -------- | ----- | --------------------- | ------------ |
| Slice A outcome semantics | Verified signed input through validation, mapping and accepted-payment lineage; customer instruction exists after successful map; business payment order after payment-lifecycle persistence | Treat upload as accepted payment order at archive time | UC-SCT-002, PAYMENTS-DOMAIN-REVIEW, E1-RECOMMENDATIONS specialist PAYMENTS_DOMAIN | Over-stating settlement/clearing readiness | Wrong story acceptance criteria and smoke expectations | PENDING |
| Slice B outcome semantics | Failed signature: durable verdict/evidence only; no parse or payment mapping; visible safe rejection without payment record | Create visible rejected-submission record with payment id | BR-SCT-003, SECURITY-CHANNEL-SIGNATURE-REVIEW | Leaking raw XML in UI | B-path confused with operational-read | PENDING |
| Validation failure visibility | Post-verify validation failures may create correlated rejected-submission; signature failures evidence-only | All failures evidence-only | PAYMENTS-DOMAIN-REVIEW | UX without safe correlation | Inconsistent operator/submitter views | PENDING |
| E1 channel profile | `REST_PLUS_BFF_UPLOAD` — REST `application/xml` command owner; BFF session adapter with CSRF/same-origin | `DIRECT_REST_ONLY`, `MANAGED_FILE_CHANNEL` | PaymentController, nextjs-bff-route skill, DEC-E1-CHANNEL | BFF security contract drift | Browser journey blocked | PENDING |
| Encoding policy | Preserve exact received bytes; never decode archived evidence with a platform-default charset; reject unsupported or contradictory encoding declarations; determine accepted encoding profile from qualified XML/ISO/EPC evidence; do not require an XML declaration unless the selected authoritative profile explicitly requires it; keep final declaration/charset policy OPEN or DECISION_BLOCKED where source evidence is insufficient | Accept default platform encoding | [ISO-MESSAGE-REVIEW.md](ISO-MESSAGE-REVIEW.md) § XML encoding and charset open questions (AI_DRAFT) | Mojibake in evidence | Parser/evidence mismatch | PENDING |
| Idempotency-Key lifecycle | One opaque Idempotency-Key identifies one logical tenant-scoped submission; transport retries of the same logical operation reuse the same key; same key + same payload identity/hash returns the original deterministic outcome; same key + materially different payload returns a deterministic conflict; key ownership, TTL and retention remain human SECURITY/DATABASE decisions | Server-generated key only | BR-SCT-002, PaymentController (implementation evidence) | Duplicate payments on retry | Data integrity failures | PENDING |
| Signature profile | `DEBINA-E1-DETACHED-ED25519-V1` candidate detached Ed25519 cryptographic/evidence profile (not EPC); normative acceptance `DECISION_BLOCKED`; archive bytes before verify | `CERTIFICATE_PROFILE`, `NO_BROWSER_SIGNATURE` | DEC-E1-SIGNATURE, signature module and PaymentController (implementation evidence only) | Wrong trust model | Invalid evidence chain | PENDING |
| Signer identity authority | Tenant-scoped signer/key registry as candidate direction; current `X-Signer-Id` header and UUID shape are implementation evidence only; signer identifier transport, representation, key binding, rotation and revocation remain DECISION_BLOCKED pending SECURITY_REVIEW | Participant certificate, user key | SECURITY-CHANNEL-SIGNATURE-REVIEW open questions | Unknown signer acceptance | Security incident | PENDING |
| XSD/TVS acquisition | Staged validation; no repo commit until lawful acquisition + checksum review | ISO XSD only interim | GAP-E1-TVS, SE-E1-SCT-TVS-001 | Copyright/licensing breach | False conformance claims | PENDING |
| Checksum pinning | Record SHA-256 after lawful acquisition; until then `VERIFY_ON_LAWFUL_ACQUISITION` | Pin from search results | SOURCE-GAP-REGISTER | Drift undetected | Wrong validation profile | PENDING |
| Intake limits | Classify-first; no production limits from council; defer JVM/HTTP measurements | Accept provisional limits | E1-EXPERIMENT-RESULTS.yaml | DoS or OOM if too loose | Service instability | PENDING |
| CreDtTm correction | Expand-contract: add `source_message_created_at` and `recorded_at`; parse `GrpHdr/CreDtTm`; stop writing receive time to `cre_dt_tm` | Rename `cre_dt_tm` in place | E1-CRE-DT-TM-CORRECTION-PROPOSAL, E1-MAP-002 | Read-model ordering regression | Incorrect audit timelines | PENDING |
| Minimal field scope | 10 MAP_AND_PERSIST, 4 VALIDATE_ONLY, 1 PRESERVE_IN_RAW_EVIDENCE, 11 DEFER_FROM_E1, 1 SOURCE_BLOCKED, 1 DECISION_BLOCKED | Full pain.001 field persistence | E1-FIELD-SCOPE-DECISION.yaml | E1 scope creep | Delayed cohort | PENDING |
| ReqdExctnDt in E1 | `DEFER_FROM_E1` — validate when admitted; not in minimal smoke persistence | MAP_AND_PERSIST now | E1-MAP-009, PAYMENTS open question | Scheduling logic missing | Product mismatch | PENDING |
| Evidence storage | Raw bytes in PostgreSQL under reviewed RLS/encryption/retention | Object storage, hash-only | ingress V10, DEC-E1-EVIDENCE | Capacity/legal exposure | Cannot reproduce signed evidence | PENDING |
| Evidence visibility | Submitter safe outcome; viewer ISO ids; auditor controlled export; no raw XML in GraphQL | Broader operator raw access | DEC-E1-EVIDENCE proposed_visibility | Data leak | Regulatory breach | PENDING |
| Retention / legal hold | `LEGAL_REGULATORY_REVIEW` required before schedule | Indefinite retain-all | GAP-RETENTION | Over-retention or premature delete | Legal non-compliance | PENDING |
| Architecture | `CURRENT_ARCHITECTURE_SUFFICIENT` | New module/aggregate/Kafka topic | MODULE-CATALOG, ADR-N1…N17, E1-RECOMMENDATIONS | Boundary creep | Re-architecture delay | PENDING |
| Approval in E1 smoke | Excluded — test under UC-SCT-APPROVAL-001 separately | Include maker-checker in smoke | PRODUCT-QA-REVIEW, HYP-APPROVAL-SMOKE | Duplicate coverage | Smoke flakiness | PENDING |
| BR-SCT-003 authority | Confirm `PROJECT-DECISION` classification; remove `iso20022-catalogue` as authority; keep normative authority DECISION_BLOCKED until an accepted dedicated security/evidence project decision or ADR exists; prepare future catalog correction only | Keep `iso20022-catalogue` as authority | UC-SCT-002, E1-RECOMMENDATIONS br_sct_003_authority_correction | Misleading compliance claims | Wrong audit trail | PENDING |
| Human review gate | All queues remain `NOT_REVIEWED` until dated human records | AI_DRAFT treated as approval | E1-APPROVALS.yaml | Fabricated approval | Unauthorized migration | PENDING |

## BR-SCT-003 authority correction (prepare only)

Current catalog entry cites `iso20022-catalogue` for signature-before-mapping.
That catalogue is not a valid authority for this ordering. The rule is already
classified `PROJECT-DECISION` in the business-rule catalog; no reclassification is
required.

- **Current classification:** `PROJECT-DECISION` (confirmed)
- **Normative authority:** `DECISION_BLOCKED` until an accepted dedicated
  security/evidence project decision or ADR exists
- **Rejected authorities (registered ids only):** `iso20022-catalogue`,
  `project-adr-n10`
- **Reviewed non-authorities (descriptive; no invented registry ids):**
  ADR-N2 (unrelated; no `project-adr-n2` registry id), ADR-N8 (unrelated; no
  `project-adr-n8` registry id), ADR-N10 / `project-adr-n10` (registered but
  unrelated to signature-before-mapping ordering)
- **Project recommendation:** Debina evidence/security policy
  (archive → verify → parse → map), grounded in `UC-SCT-002` and current
  implementation evidence only
- **Catalog edit:** not authorized in this run; apply only after owner approval

## Product / QA acceptance contract (recommended)

| Outcome | User-visible | Test layer |
|---|---|---|
| Success (A) | Submission accepted; link to payment detail | Dagger + exactly one Playwright smoke: login → upload → deterministic accepted result → payment detail |
| Invalid signature (B) | Safe rejection; no payment detail | primary Spring/security integration test; optional secondary Dagger composed rejection proof; no E1 Playwright negative smoke |
| Validation rejected | Safe reason code; correlation id | integration |
| Duplicate/idempotent | Same outcome as original | integration idempotency |
| Unauthorized | 401/403 without evidence leak | BFF component + REST |

Executable verify targets:

| Target | State |
|---|---|
| `Pain001SubmissionEndpointTest` | existing |
| `pnpm run test:pain001-upload-route` | `PLANNED_NOT_IMPLEMENTED` |
| `dagger call smoke-signed-pain001` | `PLANNED_NOT_IMPLEMENTED` |
| `e2e/e1-signed-pain001.spec.ts` | `PLANNED_NOT_IMPLEMENTED` |

## Related artifacts

- [E1-RECOMMENDATIONS.yaml](E1-RECOMMENDATIONS.yaml)
- [E1-DECISION-REGISTER.yaml](E1-DECISION-REGISTER.yaml)
- [E1-FIELD-SCOPE-DECISION.yaml](E1-FIELD-SCOPE-DECISION.yaml)
- [E1-CRE-DT-TM-CORRECTION-PROPOSAL.md](E1-CRE-DT-TM-CORRECTION-PROPOSAL.md)
- [E1-EXPERIMENT-RESULTS.yaml](E1-EXPERIMENT-RESULTS.yaml)
- [E1-APPROVALS.yaml](E1-APPROVALS.yaml)

## Proposed atomic commit plan (not executed)

See `HANDOFF.md` for commit grouping after owner manual diff review. No commits
were made in this council run.

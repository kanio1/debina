# ADR-N9 — Synthetic Credit Transfer Learning Platform Scope

## Status

Frozen

## Context

Debina needs a durable product-scope boundary before its frozen settlement and finality design is
implemented. Existing repository material defines a synthetic, deterministic Credit Transfer / ISO
20022 learning platform, while earlier assessment material also records the materially broader
requirements of a real payment hub. Treating a laboratory implementation as a CSM, designated
settlement system, or certification-ready bank would create false claims and force unapproved
product, standards, integration, and operational scope into the roadmap.

## Decision

`[FREEZE]` Debina remains an enterprise-grade **synthetic Credit Transfer / ISO 20022 learning and
reference platform** for the current roadmap. It is not a real bank, designated settlement system,
CSM, certified payment hub, or production integration.

The platform must not claim legal settlement finality, TIPS/RT1/STEP2/STET equivalence, EPC
certification, or production-integration readiness. Synthetic settlement profiles, finality rules,
receipts, and simulations are learning/reference artifacts only.

Any expansion to a full payment hub requires a new product-scope ADR that selects the target CSM,
obtains participant documentation, defines certification requirements, and establishes
standards-to-story traceability. It is not implied by this ADR or by any implementation under it.

## Consequences

- The settlement finality and LedgerPort contracts in ADR-N10 are implemented as synthetic
  laboratory behavior without legal or scheme conformance claims.
- No real CSM behavior, certification evidence, participant onboarding, receipt-based finality, or
  full-hub functionality is introduced by the current roadmap.
- The current physical `payment.received` writer stays in `payment-lifecycle`; its topic-catalog
  mismatch is accepted technical debt pending a separate superseding ownership ADR. No
  cross-schema writer is introduced to resolve it.

## Alternatives Rejected

- **Treat the current platform as a full payment hub by implementation increment** — rejected:
  product scope, CSM selection, participant documents, certification requirements, and traceability
  are absent.
- **Claim legal finality from synthetic status, delivery, receipt, or ISO evidence** — rejected:
  it contradicts the platform boundary and the frozen five-axis architecture.

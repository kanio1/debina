# Enterprise Use-Case Methodology Assurance

## Baseline and scope

Baseline reviewed: `518345a00484bbe2ebd6a9af851d15eb36964179`. Actual branch: `rebase/enterprise-evolution`; the request named `rebase/phase-b-product-domain-architecture`, but no branch switch was made because the checked-out branch already contained the required history. No production, workflow, Dagger or Wave 12 work is included.

## Findings and decisions

F-01…F-14 are confirmed: skeletal operating method, literal enforcement, actor leakage, compressed/unanchored flows, approval-goal mixing, non-behavioral slices, implied collaboration, registry-only source references, C4 metadata gaps, uneven quality measures and an overclaimed ATAM label. F-15 is partially confirmed: external CSMs and operators may be actors when outside the declared boundary; a service identity needs a boundary-specific review.

Use-Case Foundation now provides the universal actor/goal/flow base. `OUTLINE`, `ESSENTIAL` and `FULLY_DRESSED` are adaptive profiles. `ATAM_INSPIRED_DESK_REVIEW` replaces the former `atam_lite` claim; no stakeholder workshop or full ATAM is claimed. No ADR was created because this is method-operation remediation, not a new long-lived structural decision.

## Outputs and validation

Added source register, responsibility/precedence map, conformance matrix, defect/slice/use-case/C4/quality audits, AI-facilitation limits, effectiveness matrix, source-evidence policy/catalogue and structured quality catalogue. Refactored the four governance skills with explicit BA → source → architecture → planning handoffs. Added adaptive templates and structural methodology/C4 validators. The adversarial suite covers internal actors, component names, unanchored extensions, technical slices, false collaboration, READY with material questions, authority inversion and FULL_ATAM overclaim.

Deep-corrected: `UC-SCT-APPROVAL-001`, `UC-SCT-001`, `UC-SCT-003`, `UC-SCTINST-001`, `UC-STATUS-001` and `UC-AUDIT-001`. All 15 use cases and 38 slices are audited. Approval J/K/L have explicit reclassification/migration references; 35 slices remain behavioral. Baseline legacy warnings remain 296 traceability and 69 planning-semantic warnings.

## Remaining review and Phase D recommendation

Human review remains required for all AI drafts, qualified participant rail profiles, diagram readability and quality measures marked as gaps. Phase D may use the governance runner only after separate authorization; this assurance phase does not create Dagger code.

## Commit record

- `c011c9b feat(governance): assure enterprise use-case methodology` — assurance policies, skill/template remediation, audits, representative use-case corrections, validators and adversarial proofs.

## Hardening checkpoint — 2026-07-24

The installed `enterprise-use-case-engineering` skill now has mandatory source
discovery, explicit technical/quality-only exits, complete cross-skill handoff,
behavioral use-case/slice admission, and deterministic routing, regression,
adversarial and cross-skill-chain fixture-contract evals. Forward-only `ENFORCED` story validation
requires exact slice, per-claim evidence, module ownership and executable
verification references. See
`docs/governance/methodology-assurance/ENTERPRISE-USE-CASE-SKILL-HARDENING.md`.

This hardening closes skill assurance defects F-16–F-19. It does not mark
Phase E complete: controlled backlog migration and human review of AI-drafted
artifacts remain outstanding.

The earlier Phase D recommendation in this historical assurance record is
superseded by the completed, runtime-proven Phase D records. Remote CI, `act`
and deployment/release remain out of scope; completed Phase D does not block
Phase E.

# Semantic Enforcement Migration

Phase C1 introduces forward-only semantic enforcement. `LEGACY` remains warning-only for the new traceability fields; existing hard inventory rules remain hard. `PILOT` applies to explicitly marked EPIC-76 stories. A future business story becomes `ENFORCED` only when its author adds `semantic_enforcement: ENFORCED` in its story metadata (or an epic default plus explicit story opt-in). This deterministic marker is stable under moves and does not depend on Git history.

`PILOT` retains the original `use_case_slices` trace check. `ENFORCED` is the
forward contract for new or materially changed business stories and requires:

```text
use_case_id
slice_id
business_process_id
actor_or_external_system
actor_goal
main_flow
source_classification
source_evidence
applicable_rules
module_owner
architecture_realization
security_context
quality_scenarios
executable_verify
```

The semantic validator resolves those identifiers against the current use-case,
process, rule, evidence, module and quality-scenario catalogues. `READY` is
rejected when a source result is blocking, a referenced use case is
`SOURCE-BLOCKED`, or `SOURCE_CONFIRMED` relies on evidence whose version remains
`VERIFY-PER-USE`. A registry entry is discovery metadata, not per-claim
evidence. Placeholder verification and `done` with current `NOT RUN` evidence
are errors.

Technical-only work must not manufacture a business use case or actor. It
follows an explicit quality, operability, security, architecture,
infrastructure or governance scenario instead; the enterprise use-case skill
returns `QUALITY_SCENARIO_ONLY`, `ARCHITECTURE_REVIEW_ONLY`, or
`NO_USE_CASE_CHANGE`.

Phase E migrates legacy cohorts deliberately; C1 does not backfill 304 stories.

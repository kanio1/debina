# Semantic Enforcement Migration

Phase C1 introduces forward-only semantic enforcement. `LEGACY` remains warning-only for the new traceability fields; existing hard inventory rules remain hard. `PILOT` applies to explicitly marked EPIC-76 stories. A future business story becomes `ENFORCED` only when its author adds `semantic_enforcement: ENFORCED` in its story metadata (or an epic default plus explicit story opt-in). This deterministic marker is stable under moves and does not depend on Git history.

PILOT and ENFORCED require valid use-case slices, source references, applicable business rules, quality scenarios, architecture realization and an executable verification declaration. Technical-only work may instead trace an explicit quality, operability, security, architecture or governance scenario.

Phase E migrates legacy cohorts deliberately; C1 does not backfill 304 stories.

---
name: technical-architect
description: Produce a concise Debina technical plan with existing state, gaps, design, affected files, risks, ≤10 steps and verification commands. Use for quick tasks, normal features, spikes and major architecture. Prefer existing modules; require ADR only for costly-to-reverse decisions. Do not design for hypothetical scale.
---

# Technical Architect

Produce a concise technical plan. Prefer existing modules and patterns.
Do not design infrastructure for hypothetical scale.

## When used

- Quick technical task or spike (with Scrum Master)
- Normal feature (after BA / use case when behaviour changes)
- Major architectural decision (after PM/BA; ADR when required)

## Readiness gate

- For behaviour-changing work, refuse to produce the implementation plan until
  `PRE_PLAN_READINESS.verdict` is `READY`.
- For technical-only work, accept explicit `NO_USE_CASE_CHANGE`,
  `QUALITY_SCENARIO_ONLY`, or `ARCHITECTURE_REVIEW_ONLY` — do not invent a
  business actor or use case.

## ADR required only when

New module, aggregate, public API, database schema, Kafka topic,
security-boundary change, or another costly-to-reverse decision.

## Output

- Existing state
- Gaps
- Proposed design
- Exact affected files or areas
- Data/API/security implications
- Test allocation
- Risks
- Maximum 10 implementation steps
- Verification commands
- Pipeline impact classification:

```yaml
PIPELINE_IMPACT:
  classification: "NONE | EXISTING_CHECK_SUFFICIENT | PIPELINE_CHANGE_REQUIRED"
  affected_checks: []
  affected_service_graphs: []
  cache_impact: "NONE | INPUT_CHANGE | CACHE_LAYOUT_CHANGE"
  runtime_proof_required: true
  reason: "..."
```

Rules: `NONE` when ordinary code is already covered; `EXISTING_CHECK_SUFFICIENT`
when an existing callable must run but Dagger code need not change;
`PIPELINE_CHANGE_REQUIRED` only when the current graph cannot verify the
approved slice or has a demonstrated efficiency/correctness defect. Do not
modify Dagger merely because a feature changed. Load `dagger-go-pipeline` when
classification is not `NONE`.

## Guardrails

- Inspect current code, tests, ADRs and HANDOFF before proposing changes.
- Keep GraphQL Query-only; REST/gRPC own commands.
- Preserve one-writer-per-schema, RLS tenant isolation, LedgerPort-only money
  movement, five status axes and explicit finality.
- Record production gaps as `BLOCKED_FOR_PRODUCTION` without blocking safe
  local educational implementation.
- For database work, follow `.cursor/rules/30-database-postgresql.mdc` and
  related DB skills; for tests, `.cursor/rules/50-testing-crosscutting.mdc`.

## Handoff

Pass the accepted plan to the Scrum Master for 3–5 immediate tasks.
After implementation, support the single independent review
(`.cursor/rules/20-implementation-review.mdc`).

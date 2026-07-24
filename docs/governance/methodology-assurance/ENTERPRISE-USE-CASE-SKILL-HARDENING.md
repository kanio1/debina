# Enterprise Use-Case Skill Hardening

Date: 2026-07-24

Baseline: `b53a254`

Branch: `rebase/enterprise-evolution`

## Assurance finding

The installed `enterprise-use-case-engineering` skill already preserved the
Debina chain from source to runtime proof, but its triggering, source gate,
slice/readiness admission and cross-skill handoff were only partial. It had no
named routing, regression, adversarial or end-to-end eval artifacts. Registry
discovery could be mistaken for evidence, and the planning validator did not
mechanically enforce a complete `ENFORCED` story contract.

The working audit matrix remains an uncommitted `/tmp` artifact. Durable defect
outcomes are F-16–F-19 in `SKILL-DEFECT-REGISTER.yaml`.

## Hardened contract

The existing skill now classifies business changes before drafting, returns
explicit non-use-case outcomes for technical or quality-only work, and runs a
mandatory source discovery gate. A material payment rule cannot become
`READY` from a registry entry alone: per-claim evidence, version/applicability
metadata and a non-blocking payment-source classification are required.

The required handoff is:

```text
enterprise-use-case-engineering
→ source-backed-payments-modeling
→ architecture-evolution-review
→ planning-semantic-integrity
```

Use-case and slice admission remain behavioral and actor-goal based. Aggregate
names are never derived from nouns alone. Technical-only work routes to a
quality, architecture, infrastructure or governance scenario.

## Executed eval contract

`python3 tools/skills/validate-enterprise-use-case-evals.py` passed:

- routing contract: 10 positive and 10 negative cases;
- regression contract: 10 cases;
- adversarial contract: 12 fail-closed cases;
- cross-skill chain: 4 stages and 16 required outputs.

This is deterministic fixture/schema/content execution. The repository has no
documented safe evaluator for implicit model-selection behavior, so implicit
model routing is explicitly `NOT_EXECUTED` and is not claimed.

## ENFORCED readiness and negative proof

`validate-planning-semantics.py` now resolves exact use-case, slice, process,
business-rule, source-evidence, module and quality-scenario references for
stories explicitly marked `semantic_enforcement: ENFORCED`. The valid focused
fixture passes. The following fixtures fail with their causal diagnostic:

| Rejected condition | Diagnostic |
|---|---|
| READY without slice | `ESR-002` |
| READY without source classification | `ESR-007` |
| missing per-claim evidence | `ESR-008` |
| missing module owner | `ESR-010` |
| placeholder verify | `ESR-014` |
| READY with unresolved `VERIFY-PER-USE` evidence | `ESR-015` |
| done with current `NOT RUN` | `ESR-016` |
| technical-only story with invented use case | `ESR-017` |
| stale or foreign slice reference | `ESR-004` |

The skill regression/adversarial suite additionally fails closed for noun-based
aggregate invention, rail leakage into common core, participant-only behavior
without evidence, frozen-ADR change without a superseding ADR, implementation
as scheme authority, fictional technical actors, false settlement finality and
collapsed status axes.

## Scope and deferred work

No payment feature, aggregate, ADR or rail behavior was created. Existing
catalogues retain their human-review/source-blocked states. Phase E controlled
backlog migration remains open; this checkpoint deliberately does not rewrite
304 legacy stories. The next safe program step is a small, reviewed Phase E
cohort using the new `ENFORCED` contract.

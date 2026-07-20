# HANDOFF

## Zadanie

SEPA Nexus is a synthetic, deterministic SEPA/ISO 20022 testing platform. This session activated two repository-governance skills—ISO 20022 validation/lineage and Kafka payment contracts—without changing production behavior or frozen architecture.

## Zrobione

- Commit `60d20f0` created `.claude/skills/debina-iso20022-validation-lineage` and `.claude/skills/debina-kafka-payment-contract`, each with `SKILL.md`, `agents/openai.yaml`, and four direct references. `.agents/skills -> ../.claude/skills` remains the sole Codex discovery bridge; no copied skill tree was created.
- `planning/skills/skills-registry.yaml` promotes both skills to `ACTIVE`, points at `.claude/skills`, records source/trigger/boundary/overlap/reference/eval metadata, and leaves three future Wave 2 candidates planned.
- Added two routing fixture files (5 positive, 5 negative, 3 overlap, 2 pressure cases each) and two regression assertions. The ISO guardrails reject XSD-only EPC claims, unsupported-version fallback, parse-before-required-signature, identifier collapse, and ACSC-as-finality. The Kafka guardrails reject epic-title `payment.sla.breached`, pre-ack publication, unowned DLQs, invented policy, and delivery-as-finality.
- Updated `planning/skills/README.md`, `planning/skills/HANDOFF.md`, and `planning/skills/wave-1-review.md` without erasing Wave 1 evidence. The Wave 2 review found no unsupported ISO/EPC/CSM claims or invented Kafka policy.
- Passed `bash tools/skills/validate-all-skills.sh`, `git diff --check`, `python3 tools/agent-config/validate-capability-graph.py`, creator frontmatter validation, routing-fixture shape checks, and canonical discovery-path checks. Fresh `codex exec --ephemeral --sandbox read-only` explicit invocations loaded both skills and all direct references, made no edits, and rejected unsafe prompts. Implicit routing was not evaluated.

## Utknęliśmy na

Nothing is blocked for this governance task. Do not treat the newly active skills as authorization to implement the next capability tranche: EPIC-33 Story 33.3 remains source-blocked because ADR-N8 has no `payment.sla.breached` topic/payload/owner or source-backed threshold/policy; EPIC-42 Story 42.1 and EPIC-29 Story 29.1 retain their recorded missing contracts.

## Plan na następny krok

Read `planning/README.md`, `planning/BACKLOG-REDESIGN.md`, and `planning/capabilities.yaml`, then re-audit the highest-ranked not-done candidate for a newly source-backed READY capability before implementing anything.

## Pułapki, których nie wolno powtórzyć

- Preserve ADR-N9/N10/N11, the five independent status axes, one-writer-per-schema, and the synthetic-scope non-claims; never infer settlement finality from ISO status, receipt, delivery, or Kafka delivery.
- Keep `.claude/skills` as the only authoring source and `.agents/skills -> ../.claude/skills` as the symlinked discovery bridge. Never replace it or duplicate `SKILL.md` trees.
- Explicit named invocation proves discovery/loading only; do not call it implicit-routing evidence. Do not invent EPC/CSM/participant/certification rules, Kafka topics/payloads/retry counts/DLQs, or SLA policy from planning text.
- Use `apply_patch` for repository edits. Never push, reset, clean, or discard existing worktree changes.

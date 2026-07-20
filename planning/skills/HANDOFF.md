# Debina Skills handoff

## State

Wave 1 is finalized and the first two planned Wave 2 skills are active. `.claude/skills` is the one tracked authoring source and `.agents/skills -> ../.claude/skills` is the official Codex CLI discovery bridge.

## Completed

Four Debina-specific skills were created, five existing skills were hardened, 17 active skills were registered, public source provenance was pinned, and deterministic validation/eval artifacts were added. The discovery-path validator verifies the bridge, canonical real-path deduplication, all active registry entries and absence of a copied mirror. External source material was inspected as text only; no external scripts were executed or copied.

Wave 2 activation added `debina-iso20022-validation-lineage` and `debina-kafka-payment-contract` under `.claude/skills`, with direct references, routing fixtures, and regression assertions. The ISO skill blocks unsupported EPC/CSM/participant/certification claims, enforces distinct validation layers and identifier lineage, and keeps ACSC/receipt/delivery outside finality. The Kafka skill enforces ADR-N5 module-owned outbox/inbox and ADR-N8 §3.7 v2 contract ownership, including the existing `payment.sla.breached` source blocker.

## Runtime evidence

Wave 1 evidence remains: `CODEX DISCOVERY: VERIFIED`; `EXPLICIT SKILL INVOCATION: VERIFIED`; `REFERENCE LOADING: VERIFIED`; `READ-ONLY BEHAVIOR: VERIFIED`; `IMPLICIT ROUTING: NOT EXECUTED`. The verified explicit case was `$debina-payment-state-finality`; ACSC was correctly rejected as settlement-finality authority. A local Codex CLI probe could not initialize its app-server client on read-only filesystem, but the repository bridge resolves and the prior fresh-session evidence is authoritative.

Wave 2 explicit invocation is verified in fresh `codex exec --ephemeral --sandbox read-only` sessions: `$debina-iso20022-validation-lineage` and `$debina-kafka-payment-contract` each loaded `SKILL.md` and all four direct references, made no edits, and rejected the supplied unsafe assumptions. The discovery bridge and canonical physical paths were independently validator-verified. Implicit routing remains `NOT EXECUTED` because both probes named the skill.

## Next action

Run `bash tools/skills/validate-all-skills.sh` before any future skills change. Remaining Wave 2 candidates are `debina-dependency-version-gate`, `debina-next16-keycloak-bff`, and `debina-playwright-payment-lab`; do not create them without a source/readiness review. Production code was untouched.

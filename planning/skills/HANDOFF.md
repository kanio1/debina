# Debina Skills Wave 1 handoff

## State

Wave 1 is finalized. `.claude/skills` is the one tracked authoring source and `.agents/skills -> ../.claude/skills` is the official Codex CLI discovery bridge.

## Completed

Four Debina-specific skills were created, five existing skills were hardened, 17 active skills were registered, public source provenance was pinned, and deterministic validation/eval artifacts were added. The discovery-path validator verifies the bridge, canonical real-path deduplication, all active registry entries and absence of a copied mirror. External source material was inspected as text only; no external scripts were executed or copied.

## Runtime evidence

`CODEX DISCOVERY: VERIFIED`; `EXPLICIT SKILL INVOCATION: VERIFIED`; `REFERENCE LOADING: VERIFIED`; `READ-ONLY BEHAVIOR: VERIFIED`; `IMPLICIT ROUTING: NOT EXECUTED`. The verified explicit case was `$debina-payment-state-finality`; ACSC was correctly rejected as settlement-finality authority. A local Codex CLI probe could not initialize its app-server client on read-only filesystem, but the repository bridge resolves and the prior fresh-session evidence is authoritative.

## Next action

Run `bash tools/skills/validate-all-skills.sh` before any future skills change. Wave 2 remains planned: `debina-iso20022-validation-lineage`, `debina-kafka-payment-contract`, `debina-dependency-version-gate`, `debina-next16-keycloak-bff`, and `debina-playwright-payment-lab`. Production code was untouched.

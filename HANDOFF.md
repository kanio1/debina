# HANDOFF

## Zadanie

SEPA Nexus is a synthetic payment-quality learning platform. Debina Agent Skills Wave 1 is
finalized as repository governance only: one authoring source, Codex discovery bridge, provenance,
validators and eval fixtures; no payment implementation changed.

## Zrobione

- ADR-N11 remains binding: one physical PostgreSQL transaction via narrow module-owned
  `SECURITY DEFINER` commands. The verified gross-instant evidence and its no-saga/XA/`SET LOCAL
  ROLE` boundary remain unchanged.
- Commits `17798e8`, `abb3e74`, `db6669d`, `49fa0a7` and `fcf1470` establish the Wave 1 skill
  system. `.claude/skills` is the one authoring source and `.agents/skills -> ../.claude/skills`
  is the official Codex CLI discovery bridge; no copied `SKILL.md` tree exists.
- `tools/skills/check-codex-discovery-path.py` verifies the bridge stays inside the repository,
  exposes every registered active skill, resolves to the authoring source and has no divergent
  mirror. All skill/governance/planning validators pass for 17 active skills and five Wave 2 plans.
- Fresh Codex evidence verified discovery, explicit `$debina-payment-state-finality` invocation,
  its three reference loads and read-only behavior. ACSC was correctly classified as ISO/message
  status, not settlement-finality authority. Implicit routing remains `NOT EXECUTED`.
- Reviewed four approved upstream source repositories as text-only material, recorded exact commits
  and licenses, and made no production Java, TypeScript, SQL migration or runtime changes.

## Utknęliśmy na

Nothing blocks Wave 1. A local `codex-cli 0.144.6` ephemeral read-only discovery probe could not
initialize its in-process app-server client because of a read-only filesystem, but the bridge itself
resolves and the independent fresh-session explicit-invocation evidence is recorded in
`planning/skills/wave-1-review.md`.

## Plan na następny krok

Before any future skill change, run `bash tools/skills/validate-all-skills.sh`; edit only
`.claude/skills`, retain the `.agents/skills` symlink, and add explicit/implicit routing evidence
without conflating the two.

## Pułapki, których nie wolno powtórzyć

- Do not replace `.agents/skills` with copied files or create a second `SKILL.md` tree; it must
  remain a repository-local bridge to `.claude/skills`.
- Do not call explicit `$skill` invocation evidence an implicit-routing pass.
- Preserve ADR-N11: executor has function `EXECUTE` only, no direct table DML or owner membership;
  no saga/XA/`SET LOCAL ROLE`, reversal, real CSM, or inferred finality.
- V35–V43 remain append-only; correct through a higher migration only. Restore generated Modulith
  artifacts after Maven unless their change is deliberately reviewed.

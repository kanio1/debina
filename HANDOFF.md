# HANDOFF

## Zadanie

SEPA Nexus is a synthetic payment-quality learning platform. This session adds Debina Agent Skills
Wave 1 governance only: local skills, provenance, validators and eval fixtures; it does not alter
the already verified ADR-N11 gross-instant implementation.

## Zrobione

- ADR-N11 remains binding: one physical PostgreSQL transaction via narrow module-owned
  `SECURITY DEFINER` commands. The verified gross-instant evidence and its no-saga/XA/`SET LOCAL
  ROLE` boundary remain unchanged.
- Hardened five existing local skills and added four Debina-specific skills in `.claude/skills/`.
  Durable program evidence is in `planning/skills/`, with `tools/skills/validate-all-skills.sh` and
  routing/content eval fixtures.
- Reviewed four approved upstream source repositories as text-only material, recorded exact commits
  and licenses, and made no production Java, TypeScript, SQL migration or runtime changes.

## Utknęliśmy na

The requested canonical `.agents/skills/` path cannot be created in this checkout: `mkdir
.agents/skills` returns `Read-only file system`. The existing tracked `.claude/skills/` root remains
effective and is explicitly recorded in `planning/skills/HANDOFF.md`; no symlink, user-global edit,
or protected-mount workaround was used.

## Plan na następny krok

Run `bash tools/skills/validate-all-skills.sh`, inspect the skills-only commits, and report the
canonical-path environment blocker. If an owner supplies a writable `.agents` directory, move the
tracked root with `git mv`, update references and rerun validators before changing the registry path.

## Pułapki, których nie wolno powtórzyć

- Do not remove, chmod, replace, or work around the read-only `.agents` system mount.
- Preserve ADR-N11: executor has function `EXECUTE` only, no direct table DML or owner membership;
  no saga/XA/`SET LOCAL ROLE`, reversal, real CSM, or inferred finality.
- V35–V43 remain append-only; correct through a higher migration only. Restore generated Modulith
  artifacts after Maven unless their change is deliberately reviewed.

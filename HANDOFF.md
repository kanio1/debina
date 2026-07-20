# HANDOFF

## Zadanie

SEPA Nexus is a synthetic payment-quality learning platform. This session adds Debina Agent Skills
Wave 1 governance only: local skills, provenance, validators and eval fixtures; it does not alter
the already verified ADR-N11 gross-instant implementation.

## Zrobione

- ADR-N11 remains binding: one physical PostgreSQL transaction via narrow module-owned
  `SECURITY DEFINER` commands. The verified gross-instant evidence and its no-saga/XA/`SET LOCAL
  ROLE` boundary remain unchanged.
- Committed `17798e8 chore(skills): add registry validation and eval harness` and `abb3e74
  docs(skills): harden Debina agent workflows`. Five existing local skills were hardened and four
  Debina-specific skills were added in `.claude/skills/`; durable evidence is in `planning/skills/`.
- Reviewed four approved upstream source repositories as text-only material, recorded exact commits
  and licenses, and made no production Java, TypeScript, SQL migration or runtime changes.

## Utknęliśmy na

The requested canonical `.agents/skills/` path cannot be created in this checkout: `mkdir
.agents/skills` returns `Read-only file system`. The existing tracked `.claude/skills/` root remains
effective and is explicitly recorded in `planning/skills/HANDOFF.md`; no symlink, user-global edit,
or protected-mount workaround was used.

## Plan na następny krok

Obtain a writable repository-local `.agents` directory from the environment owner. Then move the
tracked root with `git mv`, update references and rerun all validators before changing registry paths.

## Pułapki, których nie wolno powtórzyć

- Do not remove, chmod, replace, or work around the read-only `.agents` system mount.
- Preserve ADR-N11: executor has function `EXECUTE` only, no direct table DML or owner membership;
  no saga/XA/`SET LOCAL ROLE`, reversal, real CSM, or inferred finality.
- V35–V43 remain append-only; correct through a higher migration only. Restore generated Modulith
  artifacts after Maven unless their change is deliberately reviewed.

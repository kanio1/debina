# Debina Skills Wave 1 handoff

## State

Wave 1 adds/hardens local skill governance, validators, provenance and eval fixtures only. The effective tracked skill root is `.claude/skills`; the requested `.agents/skills` root is blocked by a read-only system mount.

## Completed

Four Debina-specific skills were created, five existing skills were hardened, every active repository-local skill was registered, public source provenance was pinned, and deterministic validation/eval artifacts were added. External source material was inspected as text only; no external scripts were executed or copied.

## Blocker

`mkdir .agents/skills` returns `Read-only file system`. Do not remove, chmod, or replace that system mount. A later owner/environment action must provide a writable repository-local `.agents` directory before moving the effective root with `git mv` and updating references.

## Next action

Validators passed and local commits `17798e8` and `abb3e74` contain the Wave 1 work. When a writable `.agents` path exists, perform the documented canonical migration, update registry/instructions, and rerun validators.

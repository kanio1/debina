# Plan — PILOT-SPEC-KIT-CURSOR-ONLY

## Classification

- policy_profile: STANDARD
- phase: IMPLEMENTING
- approval_state: APPROVED (user chat 2026-07-29)
- write_scope: IMPLEMENTATION
- lane(compat): STANDARD
- Scope: work artifacts + `/tmp/debina-spec-kit-cursor-pilot/**` only
- Spec Kit NOT installed in Debina main repo

## Objective

Isolated Cursor Spec Kit pilot to decide adoption layout for Debina.

## Steps

1. Pin stable Spec Kit release; verify via uvx
2. Clean cursor-agent init inventory
3. Whole-directory symlink experiment
4. Per-skill bridge experiment + upgrade/uninstall
5. Runtime discovery / workflow / lifecycle / rollback
6. Main-repo integrity + adoption recommendation
7. Independent review + closeout

## Out of scope

Main-repo Spec Kit install, Skills migration, Codex, production, commit/push, `--force`.

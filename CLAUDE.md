@AGENTS.md

# Claude Code-specific guidance

- For multi-file, architectural, planning, migration, security or database changes: explore first, produce a plan, then edit.
- Use project-local skills (`.claude/skills/*`) and path-scoped instructions instead of expanding this file. Codex CLI reaches the same skill set via the symlinked fallback `tools/codex/.agents/skills` (`codex --cd tools/codex`) — do not duplicate skill content into instruction files.
- `DOCKER_HOST` for Maven/Testcontainers-via-Podman is already set via `.claude/settings.local.json`'s `env` block — never re-`export` it in a command; run `./mvnw` directly (see `backend/AGENTS.md` for the full backend command set).
- Treat Markdown instructions as guidance; deterministic safety restrictions belong in `.claude/settings.local.json` permissions/hooks, not in prose here.

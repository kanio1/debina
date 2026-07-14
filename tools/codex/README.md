# Codex project entry point

Root `.agents/` w tym środowisku jest systemowym katalogiem tylko do odczytu.

Uruchamiaj Codex dla tego repozytorium poleceniem:

```bash
codex --cd tools/codex
```

Codex uruchomiony z tego katalogu nadal pracuje wewnątrz tego samego repozytorium Git, czyta root `AGENTS.md` i wykrywa skille dostępne w `tools/codex/.agents/skills`.

Kanoniczne źródło treści skilli pozostaje w `.claude/skills`.

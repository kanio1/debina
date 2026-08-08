# Runtime / workflow test

## Cursor runtime discovery

### Attempted command (help-confirmed flags only)

```bash
cursor-agent --print --mode ask \
  --workspace /tmp/debina-spec-kit-cursor-pilot/clean-project \
  --trust \
  "Wymień dostępne Skills lub komendy Spec Kit. Nie modyfikuj plików. Nie uruchamiaj implementacji."
```

- `cursor-agent --version` → `2026.07.23-e383d2b`
- `--mode ask` documented as read-only Q&A
- Exit code: **1**
- Error: `Authentication required. Please run 'agent login' first, or set CURSOR_API_KEY`

### Result

```text
CURSOR_RUNTIME_DISCOVERY_REQUIRES_MANUAL_REOPEN
```

File presence of `.cursor/skills/speckit-*/SKILL.md` is **not** treated as full runtime discovery PASS.

## Workflow test

### Static / CLI evidence (PASS partial)

- Bundled workflow `speckit` installed: specify → review-spec → plan → review-plan → tasks → implement
- Skills encode slash workflow: specify / clarify / plan / tasks / analyze / implement / constitution / …
- Feature scaffolding via `.specify/scripts/bash/create-new-feature.sh` succeeded for two features without `--force`
- Active feature pointer: `.specify/feature.json` (`feature_directory`)
- Env hints from script: `SPECIFY_FEATURE`, `SPECIFY_FEATURE_DIRECTORY`

### Agent-driven specify→clarify→plan→tasks→analyze→implement

Not executed end-to-end: Cursor Agent authentication unavailable in this session; did not invent non-documented CLI flags; did not use `--force` / `--yolo`.

### Marks

```text
WORKFLOW_RUNTIME_TEST_PARTIAL
MANUAL_CURSOR_REOPEN_REQUIRED
```

Static skill/workflow analysis is **not** claimed as full runtime PASS.

## Constitution (disposable lifecycle project)

- Path: `.specify/memory/constitution.md`
- Written with pilot-only principles (no Debina governance copy)
- No `AGENTS.md` created by Spec Kit (duplication risk low for Cursor integration)
- Dependent templates remain under `.specify/templates/`; constitution skill text says it propagates to dependent artifacts when run via agent

## Active feature

| Step | Result |
|---|---|
| Feature 1 | `specs/001-temperature-converter/spec.md` |
| Feature 2 | `specs/002-second-feature/spec.md` |
| Current pointer after #2 | `.specify/feature.json` → `specs/002-second-feature` |
| Switch back to #1 | rewrite `feature.json` → `specs/001-temperature-converter` (verified) |
| Branch dependency | lifecycle tree had no usable git HEAD/branches (`git branch` exit 128); feature creation still worked via specs + `feature.json` |
| Agent reopen | not verified (auth); pointer is file-based, not branch-only |

## Implement

Not run (blocked on agent auth / partial workflow). No production/Debina implementation attempted.

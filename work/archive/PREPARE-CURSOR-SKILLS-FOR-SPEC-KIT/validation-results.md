# Validation results

## sync --check

```text
INFO COUNT_MATCH: eligible canonical Skills (33) == valid bridges (33)
eligible=33 errors=0
exit 0
```

## Idempotence

Second `--apply`:

```text
apply: idempotent reconcile complete
exit 0
```

Second `--check`: exit 0, still 33==33. Source hashes unchanged.

## Spec Kit namespace fixture

`test_speckit_namespace_left_alone` PASS — real `speckit-sentinel/` left alone by `--check` and `--apply`.

## Rollback fixture

- unexpected whole-link target → refused; original symlink kept
- leftover staging dir → `MIGRATION_NOT_APPLIED`; original symlink kept
- name mismatch → blocked; original symlink kept

## Frontmatter

33/33 directory names match frontmatter `name`. No collisions.

## Health check

`HEALTH_CHECK: PASS` (synthetic 37 PASS)

## verify-fast comparison

| Failure | Baseline | Post-migration |
|---|---|---|
| `git diff --check` / CLAUDE.md access | FAIL | FAIL (same root cause) |
| `HANDOFF-010` | FAIL | FAIL |
| Maven `/root/.m2` | FAIL | FAIL |
| instruction-hygiene PermissionError on CLAUDE.md | (same CLAUDE.md access; may appear when changed-file set expands) | FAIL — same inaccessible `CLAUDE.md` |
| skills validation suite | n/a / not a new regression | PASS |

```text
NO_NEW_VERIFY_FAST_FAILURES
```

Standalone `git diff --check` (outside verify-fast packaging): PASS.

## Skills validation (via verify-fast)

`bash tools/skills/validate-all-skills.sh` → RESULT: PASS (33 active via `.agents/skills`).

## Runtime discovery

```text
STATIC_CURSOR_SKILLS_LAYOUT_VALIDATED
MANUAL_CURSOR_REOPEN_REQUIRED
```

### Manual reopen instruction

1. Zamknij bieżące okno Cursor.
2. Ponownie otwórz repozytorium Debina.
3. Uruchom nowy Agent chat.
4. Poproś: „Wymień dostępne Skills Debiny i wskaż ścieżki przynajmniej trzech z nich. Nie zmieniaj plików.”
5. Potwierdź, że wykryte zostały Skills z `.cursor/skills`.
6. Nie instaluj jeszcze Spec Kit, jeżeli Skills nie są widoczne.

## Independent review verdict

```text
PASS_WITH_FINDINGS
```

Finding: `MANUAL_CURSOR_REOPEN_REQUIRED` only (for runtime discovery).

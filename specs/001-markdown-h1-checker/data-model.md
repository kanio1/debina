# Data Model: Markdown Single-H1 Checker

## Entities

### MarkdownFile

| Field | Type | Notes |
|-------|------|-------|
| path | string | CLI argument |
| content | string | UTF-8 decoded text |
| h1_count | int | ATX H1 outside fences |

### CheckResult

| Field | Type | Values |
|-------|------|--------|
| path | string | as given |
| status | enum | PASS, FAIL, ERROR |
| h1_count | int? | for PASS/FAIL |
| reason | string? | for ERROR |

### CliExitCode

| Value | Meaning |
|-------|---------|
| 0 | all PASS |
| 1 | at least one FAIL, no ERROR |
| 2 | any ERROR or usage error |

## State: Fence scanner

- `in_fence: bool`
- `fence_char: str | None` (`` ` `` or `~`)

Toggle on lines matching `^(`{3,}|~{3,})\s*$` with matching close fence.

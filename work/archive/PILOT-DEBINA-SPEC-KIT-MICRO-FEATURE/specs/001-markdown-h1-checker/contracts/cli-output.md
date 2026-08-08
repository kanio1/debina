# CLI Output Contract

## Result lines (stdout)

```
PASS <path> H1=1
FAIL <path> H1=<count>
ERROR <path> <reason>
```

## Exit codes

| Code | Condition |
|------|-----------|
| 0 | All files readable; each has exactly 1 H1 |
| 1 | At least one FAIL; no ERROR |
| 2 | Any ERROR or usage error |

## Usage (stderr)

```
usage: markdown_h1_check.py FILE [FILE ...]
```

Exit 2 when no FILE arguments.

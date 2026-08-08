# Quickstart: Markdown Single-H1 Checker (post-implementation)

**Prerequisites**: Phase B implementation complete; `approval_state=APPROVED`.

## Verify single file

```bash
python3 tools/spec-kit-pilot/markdown_h1_check.py docs/example.md
echo "exit=$?"
```

Expected: `PASS docs/example.md H1=1` and `exit=0` for valid single-H1 file.

## Verify batch

```bash
python3 tools/spec-kit-pilot/markdown_h1_check.py file1.md file2.md
```

Output order matches argument order.

## Run tests

```bash
python3 -m unittest tests/tools/spec-kit-pilot/test_markdown_h1_check.py
```

## Compile check

```bash
python3 -m py_compile tools/spec-kit-pilot/markdown_h1_check.py \
  tests/tools/spec-kit-pilot/test_markdown_h1_check.py
```

**Note**: Phase B complete — commands verified 2026-08-08.

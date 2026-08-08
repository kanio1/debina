#!/usr/bin/env python3
"""Read-only CLI: validate Markdown files contain exactly one ATX H1 (# )."""
from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path

H1_RE = re.compile(r"^# ")


@dataclass(frozen=True)
class CheckResult:
    path: str
    status: str
    h1_count: int | None = None
    reason: str | None = None

    def format_line(self) -> str:
        if self.status == "PASS":
            return f"PASS {self.path} H1={self.h1_count}"
        if self.status == "FAIL":
            return f"FAIL {self.path} H1={self.h1_count}"
        return f"ERROR {self.path} {self.reason}"


def count_atx_h1(text: str) -> int:
    in_fence = False
    fence_char: str | None = None
    count = 0

    for line in text.splitlines():
        if in_fence:
            if fence_char and re.match(
                rf"^{re.escape(fence_char)}{{3,}}\s*$", line
            ):
                in_fence = False
                fence_char = None
            continue

        open_match = re.match(r"^(`{3,}|~{3,})", line)
        if open_match:
            in_fence = True
            fence_char = open_match.group(1)[0]
            continue

        if H1_RE.match(line):
            count += 1

    return count


def analyze_file(path: str) -> CheckResult:
    file_path = Path(path)

    if not file_path.exists():
        return CheckResult(path=path, status="ERROR", reason="file not found")
    if file_path.is_dir():
        return CheckResult(path=path, status="ERROR", reason="not a file")

    try:
        text = file_path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return CheckResult(path=path, status="ERROR", reason="invalid utf-8")
    except OSError as exc:
        return CheckResult(path=path, status="ERROR", reason=str(exc))

    h1_count = count_atx_h1(text)
    if h1_count == 1:
        return CheckResult(path=path, status="PASS", h1_count=h1_count)
    return CheckResult(path=path, status="FAIL", h1_count=h1_count)


def aggregate_exit_code(results: list[CheckResult]) -> int:
    if any(result.status == "ERROR" for result in results):
        return 2
    if any(result.status == "FAIL" for result in results):
        return 1
    return 0


def main(argv: list[str] | None = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if not args:
        print("usage: markdown_h1_check.py FILE [FILE ...]", file=sys.stderr)
        return 2

    results = [analyze_file(path) for path in args]
    for result in results:
        print(result.format_line())
    return aggregate_exit_code(results)


if __name__ == "__main__":
    raise SystemExit(main())

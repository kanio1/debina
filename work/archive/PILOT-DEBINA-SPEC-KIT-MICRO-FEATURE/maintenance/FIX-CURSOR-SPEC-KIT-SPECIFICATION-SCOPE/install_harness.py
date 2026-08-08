#!/usr/bin/env python3
from __future__ import annotations

import gzip
import py_compile
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]
BUNDLE = Path(__file__).resolve().parent / "harness_bundle.gz"
SEP = b"\n---FILE---\n"


def main():
    if not BUNDLE.is_file():
        print("missing bundle", file=sys.stderr)
        return 2
    raw = gzip.decompress(BUNDLE.read_bytes())
    parts = raw.split(SEP)
    written = []
    for part in parts:
        if not part.strip():
            continue
        nl = part.index(b"\n")
        rel = part[:nl].decode("utf-8")
        content = part[nl + 1 :].decode("utf-8")
        dest = REPO / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(content, encoding="utf-8")
        written.append(rel)
    probe = REPO / "tools/agent_policy/_bootstrap_probe.txt"
    if probe.is_file():
        probe.unlink()
    for rel in written:
        if rel.endswith(".py"):
            py_compile.compile(str(REPO / rel), doraise=True)
    print("wrote", len(written))
    for rel in written:
        print(rel)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

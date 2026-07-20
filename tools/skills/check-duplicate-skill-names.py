#!/usr/bin/env python3
"""Detect duplicate repository-local skill names; report home duplicates only."""
from __future__ import annotations
import os, re, sys
from collections import defaultdict
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
def name(path: Path) -> str | None:
    m = re.search(r"^name:\s*[\"']?([^\"'\n]+)", path.read_text(encoding="utf-8"), re.M)
    return m.group(1).strip() if m else None
repo = defaultdict(list)
seen_paths: set[Path] = set()
for base in (ROOT / ".agents" / "skills", ROOT / ".claude" / "skills", ROOT / ".codex" / "skills"):
    if base.is_dir():
        for p in base.glob("*/SKILL.md"): repo[name(p)].append(p.relative_to(ROOT).as_posix())
physical = defaultdict(set)
for base in (ROOT / ".agents" / "skills", ROOT / ".claude" / "skills", ROOT / ".codex" / "skills"):
    if base.is_dir():
        for p in base.glob("*/SKILL.md"):
            physical[name(p)].add(p.resolve())
bad = {n: sorted(str(x.relative_to(ROOT)) for x in ps) for n, ps in physical.items() if n and len(ps) > 1}
if bad:
    print("DUPLICATES: FAIL"); [print(f"{n}: {', '.join(ps)}") for n, ps in bad.items()]; sys.exit(1)
home = Path.home()
reported = []
for base in (home / ".agents" / "skills", home / ".codex" / "skills", home / ".claude" / "skills"):
    if base.is_dir(): reported += [str(p) for p in base.glob("*/SKILL.md") if name(p) in repo]
print(f"DUPLICATES: PASS ({len(repo)} repository names; user-global duplicates reported={len(reported)})")
for p in reported: print(f"WARN user-global duplicate: {p}")

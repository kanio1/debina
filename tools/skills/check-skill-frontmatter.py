#!/usr/bin/env python3
"""Validate active skill frontmatter using only the Python standard library."""
from __future__ import annotations
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SKILLS = ROOT / ".agents" / "skills"
if not SKILLS.is_dir():
    SKILLS = ROOT / ".claude" / "skills"

def frontmatter(path: Path) -> dict[str, str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    if not lines or lines[0] != "---": raise ValueError("missing opening delimiter")
    try: end = lines.index("---", 1)
    except ValueError: raise ValueError("missing closing delimiter")
    result: dict[str, str] = {}
    for line in lines[1:end]:
        if not line.strip() or line.lstrip().startswith("#"): continue
        # Nested YAML lists/maps belong to the preceding top-level key. The
        # validator deliberately needs only top-level name/description.
        if line[0].isspace() or line.lstrip().startswith("-"):
            continue
        if ":" not in line: raise ValueError(f"invalid frontmatter line: {line}")
        key, value = line.split(":", 1); key, value = key.strip(), value.strip().strip('"\'')
        if key in result: raise ValueError(f"duplicate field: {key}")
        result[key] = value
    return result

errors: list[str] = []
for skill in sorted(SKILLS.glob("*/")):
    md = skill / "SKILL.md"
    if not md.is_file(): errors.append(f"{skill.relative_to(ROOT)}: active directory lacks SKILL.md"); continue
    try: meta = frontmatter(md)
    except ValueError as exc: errors.append(f"{md.relative_to(ROOT)}: {exc}"); continue
    if not meta.get("name"): errors.append(f"{md.relative_to(ROOT)}: missing name")
    elif meta["name"] != skill.name: errors.append(f"{md.relative_to(ROOT)}: name does not equal directory")
    elif not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", meta["name"]): errors.append(f"{md.relative_to(ROOT)}: invalid name")
    if not meta.get("description"): errors.append(f"{md.relative_to(ROOT)}: empty description")
if errors:
    print("FRONTMATTER: FAIL"); print("\n".join(errors)); sys.exit(1)
print(f"FRONTMATTER: PASS ({len(list(SKILLS.glob('*/SKILL.md')))} active skills, root={SKILLS.relative_to(ROOT)})")

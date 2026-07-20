#!/usr/bin/env python3
"""Verify the single-source Codex repository discovery bridge."""
from __future__ import annotations

import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2].resolve()
BRIDGE = ROOT / ".agents" / "skills"
AUTHORING = ROOT / ".claude" / "skills"


def inside_repo(path: Path) -> bool:
    try:
        path.resolve(strict=True).relative_to(ROOT)
        return True
    except (FileNotFoundError, ValueError, OSError):
        return False


errors: list[str] = []
if not os.path.lexists(BRIDGE):
    errors.append(".agents/skills does not exist")
elif BRIDGE.is_symlink() and not BRIDGE.exists():
    errors.append(".agents/skills is a broken symlink")
elif not BRIDGE.is_dir():
    errors.append(".agents/skills is neither a directory nor a valid symlink to one")

if not errors:
    bridge_real = BRIDGE.resolve(strict=True)
    if not inside_repo(BRIDGE):
        errors.append(f".agents/skills escapes repository: {bridge_real}")
    if AUTHORING.is_dir() and bridge_real != AUTHORING.resolve():
        errors.append(".agents/skills is a copied or divergent mirror, not the shared authoring source")

    for candidate in BRIDGE.rglob("*"):
        if candidate.is_symlink():
            try:
                resolved = candidate.resolve(strict=True)
            except OSError:
                errors.append(f"broken symlink: {candidate.relative_to(ROOT)}")
                continue
            if not inside_repo(candidate):
                errors.append(f"symlink escapes repository: {candidate.relative_to(ROOT)} -> {resolved}")

registry = json.loads((ROOT / "planning/skills/skills-registry.yaml").read_text(encoding="utf-8"))
active = [item for item in registry["skills"] if item["status"] == "ACTIVE"]
real_by_name: dict[str, Path] = {}
for item in active:
    name = item["name"]
    discovery = BRIDGE / name / "SKILL.md"
    authoring = ROOT / item["path"] / "SKILL.md"
    if not discovery.is_file():
        errors.append(f"active skill is not reachable through Codex discovery path: {name}")
        continue
    if not inside_repo(discovery):
        errors.append(f"active discovery path escapes repository: {name}")
        continue
    if not authoring.is_file():
        errors.append(f"active registry authoring path is missing: {name}")
        continue
    if discovery.resolve() != authoring.resolve():
        errors.append(f"discovery path is not the same physical SKILL.md as authoring source: {name}")
    previous = real_by_name.get(name)
    if previous is not None and previous != discovery.resolve():
        errors.append(f"duplicate physical paths for skill name: {name}")
    real_by_name[name] = discovery.resolve()

if not any(BRIDGE.glob("*/SKILL.md")):
    errors.append(".agents/skills exposes no active SKILL.md files")
if errors:
    print("CODEX DISCOVERY PATH: FAIL")
    print("\n".join(errors))
    sys.exit(1)
print(f"CODEX DISCOVERY PATH: PASS ({len(active)} active skills via {BRIDGE.relative_to(ROOT)} -> {BRIDGE.resolve().relative_to(ROOT)})")

#!/usr/bin/env python3
"""Write pilot constitution and switch active feature (no git writes)."""
from __future__ import annotations

import json
from pathlib import Path

life = Path("/tmp/debina-spec-kit-cursor-pilot/lifecycle-project")
const = life / ".specify" / "memory" / "constitution.md"
const.write_text(
    """# Pilot Temperature Converter Constitution

## Core Principles

### I. No production secrets
Never store or request production secrets in this pilot project.

### II. Tests required for behavior changes
Any behavior change must include unit tests.

### III. No Git push
Do not push to remote repositories.

### IV. Implementation must match specification
Code must implement only what the specification requires.

## Additional Constraints
This is a disposable Spec Kit pilot. No Debina governance content.
""",
    encoding="utf-8",
)

# Active feature is stored in .specify/feature.json (not only branch name).
feature_path = life / ".specify" / "feature.json"
before = feature_path.read_text(encoding="utf-8") if feature_path.exists() else None
feature_path.write_text(
    json.dumps({"feature_directory": "specs/001-temperature-converter"}) + "\n",
    encoding="utf-8",
)
after = feature_path.read_text(encoding="utf-8")

# Check whether create-new-feature also created git branches (read-only).
import subprocess

br = subprocess.run(
    ["git", "branch", "--list"],
    cwd=str(life),
    capture_output=True,
    text=True,
    check=False,
)
head = subprocess.run(
    ["git", "rev-parse", "--abbrev-ref", "HEAD"],
    cwd=str(life),
    capture_output=True,
    text=True,
    check=False,
)

out = {
    "constitution_path": str(const.relative_to(life)),
    "constitution_bytes": const.stat().st_size,
    "feature_before": before,
    "feature_after": after,
    "git_branch_list_code": br.returncode,
    "git_branch_list": br.stdout,
    "git_head_branch": head.stdout.strip(),
    "agents_md_exists": (life / "AGENTS.md").exists(),
    "note": "Active feature tracked in .specify/feature.json; SPECIFY_FEATURE env also documented by create-new-feature.sh",
}
ev = Path("/tmp/debina-spec-kit-cursor-pilot/evidence/constitution-active-feature.json")
ev.write_text(json.dumps(out, indent=2) + "\n", encoding="utf-8")
print(json.dumps(out, indent=2))

#!/usr/bin/env python3
"""Spec Kit experiments B/C without --force: init empty, then restructure + upgrade/uninstall."""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
from pathlib import Path

SPEC = [
    "uvx",
    "--from",
    "git+https://github.com/github/spec-kit.git@v0.14.3",
    "specify",
]
ROOT = Path("/tmp/debina-spec-kit-cursor-pilot")
EVIDENCE = ROOT / "evidence"
SENTINEL = """---
name: debina-sentinel
description: Synthetic Debina skill used only for Spec Kit pilot collision tests.
---

# debina-sentinel

Safe synthetic skill. Do not use in production Debina.
"""


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(cmd: list[str], cwd: Path | None = None) -> dict:
    proc = subprocess.run(cmd, cwd=str(cwd) if cwd else None, capture_output=True, text=True, check=False)
    return {
        "cmd": cmd,
        "cwd": str(cwd) if cwd else None,
        "code": proc.returncode,
        "stdout": proc.stdout[-5000:],
        "stderr": proc.stderr[-5000:],
    }


def inventory(root: Path) -> dict:
    files, links = [], []
    if not root.exists():
        return {"root": str(root), "files": [], "links": []}
    for p in sorted(root.rglob("*")):
        rel = str(p.relative_to(root))
        if p.is_symlink():
            links.append({"path": rel, "target": os.readlink(p), "resolved": str(p.resolve())})
        elif p.is_file():
            files.append({"path": rel, "sha256": sha(p), "size": p.stat().st_size})
    return {"root": str(root), "files": files, "links": links}


def dump(name: str, data: object) -> None:
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    (EVIDENCE / name).write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def init_empty(project: Path) -> dict:
    if project.exists():
        shutil.rmtree(project)
    return run(
        SPEC
        + [
            "init",
            str(project),
            "--integration",
            "cursor-agent",
            "--script",
            "sh",
            "--ignore-agent-tools",
        ]
    )


def try_init_on_nonempty(project: Path) -> dict:
    return run(
        SPEC
        + [
            "init",
            ".",
            "--here",
            "--integration",
            "cursor-agent",
            "--script",
            "sh",
            "--ignore-agent-tools",
        ],
        cwd=project,
    )


def experiment_b() -> dict:
    # Phase 1: create symlink layout first, attempt init without --force
    pre = ROOT / "whole-directory-symlink-pre"
    if pre.exists():
        shutil.rmtree(pre)
    sentinel_dir = pre / ".claude" / "skills" / "debina-sentinel"
    sentinel_dir.mkdir(parents=True)
    skill = sentinel_dir / "SKILL.md"
    skill.write_text(SENTINEL, encoding="utf-8")
    (pre / ".cursor").mkdir(parents=True)
    (pre / ".cursor" / "skills").symlink_to("../.claude/skills")
    pre_hash = sha(skill)
    init_nonempty = try_init_on_nonempty(pre)

    # Phase 2: clean init then convert .cursor/skills into whole-dir symlink including sentinel
    project = ROOT / "whole-directory-symlink-project"
    init = init_empty(project)
    skills = project / ".cursor" / "skills"
    claude_skills = project / ".claude" / "skills"
    claude_skills.mkdir(parents=True)
    # move managed skills into .claude/skills
    for child in list(skills.iterdir()):
        shutil.move(str(child), str(claude_skills / child.name))
    # add sentinel
    sdir = claude_skills / "debina-sentinel"
    sdir.mkdir()
    sfile = sdir / "SKILL.md"
    sfile.write_text(SENTINEL, encoding="utf-8")
    sentinel_hash = sha(sfile)
    skills.rmdir()
    skills.symlink_to("../.claude/skills")
    before_upgrade = inventory(project)
    upgrade = run(SPEC + ["integration", "upgrade", "cursor-agent"], cwd=project)
    after_upgrade = inventory(project)
    claude_names = sorted(p.name for p in claude_skills.iterdir())
    unsafe = skills.is_symlink() and any(
        (claude_skills / n).exists() and n.startswith("speckit-") for n in claude_names
    )
    # Also check if upgrade wrote through symlink (speckit still under .claude)
    uninstall = run(SPEC + ["integration", "uninstall", "cursor-agent"], cwd=project)
    after_uninstall = inventory(project)
    sentinel_after_un = sha(sfile) if sfile.exists() else None
    return {
        "init_on_existing_symlink_layout": init_nonempty,
        "clean_init": init,
        "restructure_before_upgrade": before_upgrade,
        "upgrade": upgrade,
        "after_upgrade": after_upgrade,
        "sentinel_hash_before_upgrade": sentinel_hash,
        "sentinel_hash_after_upgrade": sha(sfile) if sfile.exists() else None,
        "cursor_skills_is_symlink": skills.is_symlink(),
        "claude_skills_entries_after_upgrade": claude_names,
        "WHOLE_DIRECTORY_SYMLINK_UNSAFE": True if skills.is_symlink() else False,
        "note": "Managed Spec Kit skills lived under symlink target .claude/skills; install/upgrade through whole-dir symlink shares Debina canonical tree.",
        "uninstall": uninstall,
        "after_uninstall": after_uninstall,
        "sentinel_survived_uninstall": sentinel_after_un == sentinel_hash,
        "pre_layout_sentinel_hash": pre_hash,
    }


def experiment_c() -> dict:
    # Phase 1: bridges first, init without --force
    pre = ROOT / "per-skill-bridges-pre"
    if pre.exists():
        shutil.rmtree(pre)
    src = pre / "agent-skills-src" / "debina-sentinel"
    src.mkdir(parents=True)
    skill = src / "SKILL.md"
    skill.write_text(SENTINEL, encoding="utf-8")
    skills = pre / ".cursor" / "skills"
    skills.mkdir(parents=True)
    link = skills / "debina-sentinel"
    link.symlink_to("../../agent-skills-src/debina-sentinel")
    pre_hash = sha(skill)
    init_nonempty = try_init_on_nonempty(pre)

    # Phase 2: clean init then add per-skill bridge beside speckit-*
    project = ROOT / "per-skill-bridges-project"
    init = init_empty(project)
    src2 = project / "agent-skills-src" / "debina-sentinel"
    src2.mkdir(parents=True)
    skill2 = src2 / "SKILL.md"
    skill2.write_text(SENTINEL, encoding="utf-8")
    skills2 = project / ".cursor" / "skills"
    link2 = skills2 / "debina-sentinel"
    link2.symlink_to("../../agent-skills-src/debina-sentinel")
    h = sha(skill2)
    before = {
        "readlink": os.readlink(link2),
        "resolved": str(link2.resolve()),
        "hash": h,
        "entries": sorted(p.name for p in skills2.iterdir()),
    }
    upgrade = run(SPEC + ["integration", "upgrade", "cursor-agent"], cwd=project)
    mid = {
        "link_still_symlink": link2.is_symlink(),
        "readlink": os.readlink(link2) if link2.is_symlink() else None,
        "hash": sha(skill2) if skill2.exists() else None,
        "entries": sorted(p.name for p in skills2.iterdir()) if skills2.exists() else [],
        "upgrade": upgrade,
    }
    # modify one managed file
    managed = skills2 / "speckit-specify" / "SKILL.md"
    managed_before = sha(managed)
    managed.write_text(managed.read_text(encoding="utf-8") + "\n# PILOT_MARKER\n", encoding="utf-8")
    upgrade2 = run(SPEC + ["integration", "upgrade", "cursor-agent"], cwd=project)
    managed_after_upgrade = sha(managed) if managed.exists() else None
    uninstall = run(SPEC + ["integration", "uninstall", "cursor-agent"], cwd=project)
    after_un = {
        "entries": sorted(p.name for p in skills2.iterdir()) if skills2.exists() else [],
        "link_still_symlink": link2.is_symlink(),
        "sentinel_hash": sha(skill2) if skill2.exists() else None,
        "managed_exists": managed.exists(),
        "managed_hash": sha(managed) if managed.exists() else None,
        "uninstall": uninstall,
    }
    return {
        "init_on_existing_bridge_layout": init_nonempty,
        "clean_init": init,
        "before_upgrade": before,
        "after_upgrade": mid,
        "modified_managed_before": managed_before,
        "modified_managed_after_second_upgrade": managed_after_upgrade,
        "modified_file_preserved_or_blocked": managed_after_upgrade == managed_before
        or upgrade2["code"] != 0,
        "second_upgrade": upgrade2,
        "after_uninstall": after_un,
        "sentinel_untouched": after_un["sentinel_hash"] == h,
        "bridge_survived_uninstall": after_un["link_still_symlink"] and after_un["sentinel_hash"] == h,
        "pre_hash": pre_hash,
    }


def main() -> int:
    b = experiment_b()
    dump("experiment-b-whole-symlink.json", b)
    c = experiment_c()
    dump("experiment-c-per-skill.json", c)
    summary = {
        "B_init_nonempty_code": b["init_on_existing_symlink_layout"]["code"],
        "B_WHOLE_DIRECTORY_SYMLINK_UNSAFE": b["WHOLE_DIRECTORY_SYMLINK_UNSAFE"],
        "B_sentinel_survived_uninstall": b["sentinel_survived_uninstall"],
        "C_init_nonempty_code": c["init_on_existing_bridge_layout"]["code"],
        "C_bridge_after_upgrade": c["after_upgrade"]["link_still_symlink"],
        "C_hash_after_upgrade": c["after_upgrade"]["hash"] == c["before_upgrade"]["hash"],
        "C_modified_protected": c["modified_file_preserved_or_blocked"],
        "C_bridge_survived_uninstall": c["bridge_survived_uninstall"],
        "C_entries_after_upgrade": c["after_upgrade"]["entries"],
    }
    dump("experiments-bc-summary.json", summary)
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

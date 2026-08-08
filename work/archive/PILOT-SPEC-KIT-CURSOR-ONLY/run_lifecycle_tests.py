#!/usr/bin/env python3
"""Lifecycle tests: feature scripts, upgrade idempotence, rollback conflict, hooks/rules presence."""
from __future__ import annotations

import json
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


def run(cmd, cwd=None):
    p = subprocess.run(cmd, cwd=str(cwd) if cwd else None, capture_output=True, text=True, check=False)
    return {"cmd": cmd, "cwd": str(cwd) if cwd else None, "code": p.returncode, "stdout": p.stdout[-6000:], "stderr": p.stderr[-6000:]}


def dump(name, data):
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    (EVIDENCE / name).write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    # lifecycle project from clean copy
    src = ROOT / "clean-project"
    life = ROOT / "lifecycle-project"
    if life.exists():
        shutil.rmtree(life)
    shutil.copytree(src, life)

    # Idempotent upgrade on clean
    up1 = run(SPEC + ["integration", "upgrade", "cursor-agent"], cwd=life)
    up2 = run(SPEC + ["integration", "upgrade", "cursor-agent"], cwd=life)
    st = run(SPEC + ["integration", "status"], cwd=life)

    # Feature creation via bundled script
    create = run(["bash", ".specify/scripts/bash/create-new-feature.sh", "temperature-converter"], cwd=life)
    specs = sorted(str(p.relative_to(life)) for p in (life / "specs").rglob("*") if p.is_file()) if (life / "specs").exists() else []
    # second feature
    create2 = run(["bash", ".specify/scripts/bash/create-new-feature.sh", "second-feature"], cwd=life)
    specs2 = sorted(str(p.relative_to(life)) for p in (life / "specs").rglob("*") if p.is_file()) if (life / "specs").exists() else []

    # Active feature markers
    markers = []
    for cand in [
        life / ".specify" / "active-feature.json",
        life / ".specify" / "feature.json",
        life / ".specify" / "state.json",
        life / ".git" / "HEAD",
    ]:
        if cand.exists():
            markers.append({"path": str(cand.relative_to(life)), "text": cand.read_text(encoding="utf-8", errors="replace")[:500]})
    # search for feature id files
    for p in life.rglob("*"):
        if p.is_file() and any(k in p.name.lower() for k in ("active", "feature", "current")):
            if ".git" in p.parts:
                continue
            markers.append({"path": str(p.relative_to(life)), "text": p.read_text(encoding="utf-8", errors="replace")[:300]})

    # Rules / hooks presence
    rules = list((life / ".cursor" / "rules").rglob("*")) if (life / ".cursor" / "rules").exists() else []
    hooks = list((life / ".cursor" / "hooks").rglob("*")) if (life / ".cursor" / "hooks").exists() else []
    agents_md = (life / "AGENTS.md").exists()

    # Rollback: file where directory expected under skills
    rollback = ROOT / "rollback-project"
    if rollback.exists():
        shutil.rmtree(rollback)
    shutil.copytree(src, rollback)
    conflict = rollback / ".cursor" / "skills" / "speckit-NEW-SHOULD-FAIL"
    conflict.write_text("I am a file not a directory\n", encoding="utf-8")
    # Also put a file blocking a known install path by replacing a skill dir with file after delete?
    # Try reinstall upgrade after corrupting structure
    before = sorted(p.name for p in (rollback / ".cursor" / "skills").iterdir())
    # Remove one skill dir and replace with file of same name to provoke conflict on upgrade
    victim = rollback / ".cursor" / "skills" / "speckit-analyze"
    if victim.exists():
        shutil.rmtree(victim)
        victim.write_text("BLOCK\n", encoding="utf-8")
    rb_up = run(SPEC + ["integration", "upgrade", "cursor-agent"], cwd=rollback)
    after = inventory_names(rollback)
    victim_state = "file" if victim.is_file() else ("dir" if victim.is_dir() else "missing")

    # Workflow list
    wf = run(SPEC + ["workflow", "list"], cwd=life)
    wf_info = run(SPEC + ["workflow", "info", "speckit"], cwd=life)

    # Cursor agent discovery (non-destructive help only; no invented flags)
    ca_help = run(["cursor-agent", "--help"])

    out = {
        "upgrade_idempotent_1": up1,
        "upgrade_idempotent_2": up2,
        "status_after_upgrade": st,
        "create_feature_1": create,
        "specs_after_1": specs,
        "create_feature_2": create2,
        "specs_after_2": specs2,
        "feature_markers": markers,
        "cursor_rules_generated": [str(p.relative_to(life)) for p in rules],
        "cursor_hooks_generated": [str(p.relative_to(life)) for p in hooks],
        "agents_md_created": agents_md,
        "rollback": {
            "before_skills": before,
            "upgrade": rb_up,
            "after_skills": after,
            "victim_state": victim_state,
            "filesystem_still_has_block_file": victim.is_file() and victim.read_text() == "BLOCK\n",
        },
        "workflow_list": wf,
        "workflow_info_speckit": wf_info,
        "cursor_agent_help": {"code": ca_help["code"], "stdout_tail": ca_help["stdout"][-2500:]},
        "HOOK_INTERACTION": "NO_HOOK_INTERACTION" if not hooks else "CONFLICT",
        "RULES_IMPACT": "NONE_GENERATED" if not rules else "CREATED_RULES",
    }
    dump("lifecycle-tests.json", out)
    print(json.dumps({
        "upgrade1": up1["code"],
        "upgrade2": up2["code"],
        "feature1": create["code"],
        "feature2": create2["code"],
        "specs1": len(specs),
        "specs2": len(specs2),
        "rules": len(rules),
        "hooks": len(hooks),
        "agents_md": agents_md,
        "rollback_code": rb_up["code"],
        "victim": victim_state,
        "hook_interaction": out["HOOK_INTERACTION"],
        "rules_impact": out["RULES_IMPACT"],
    }, indent=2))
    return 0


def inventory_names(root: Path):
    skills = root / ".cursor" / "skills"
    if not skills.exists():
        return []
    return sorted(p.name for p in skills.iterdir())


if __name__ == "__main__":
    raise SystemExit(main())

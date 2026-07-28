#!/usr/bin/env python3
"""Synthetic health checks for Debina lean hooks. Never executes dangerous commands."""
from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
GUARD = REPO / ".cursor" / "hooks" / "command-guard.py"
SCOPE = REPO / ".cursor" / "hooks" / "scope-approval-gate.py"
CHECK = REPO / ".cursor" / "hooks" / "checkpoint.py"


def run_hook(script: Path, payload: dict) -> dict:
    proc = subprocess.run(
        [sys.executable, str(script)],
        input=json.dumps(payload),
        text=True,
        capture_output=True,
        cwd=str(REPO),
        check=False,
    )
    stdout = proc.stdout.strip().splitlines()
    if not stdout:
        return {"_raw": proc.stdout, "_stderr": proc.stderr, "_code": proc.returncode}
    try:
        return json.loads(stdout[-1])
    except json.JSONDecodeError:
        return {"_raw": proc.stdout, "_stderr": proc.stderr, "_code": proc.returncode}


def expect(name: str, cond: bool, detail: str = "") -> None:
    if cond:
        print(f"PASS {name}")
    else:
        print(f"FAIL {name}: {detail}")
        raise SystemExit(1)


def main() -> int:
    # 1 safe command
    r = run_hook(GUARD, {"command": "git status --short"})
    expect("safe-command-allow", r.get("permission") == "allow", str(r))

    # 2 git push blocked (synthetic — not executed)
    r = run_hook(GUARD, {"command": "git push origin HEAD"})
    expect("git-push-deny", r.get("permission") == "deny", str(r))

    # 3 rm -rf blocked
    r = run_hook(GUARD, {"command": "rm -rf /tmp/anything"})
    expect("rm-deny", r.get("permission") == "deny", str(r))

    active_path = REPO / "work" / "ACTIVE.json"
    had_active = active_path.is_file()
    previous = active_path.read_text(encoding="utf-8") if had_active else None

    try:
        task = "HEALTH-FAST"
        task_dir = REPO / "work" / "active" / task
        task_dir.mkdir(parents=True, exist_ok=True)
        (task_dir / "plan.md").write_text("# plan\nsteps\n", encoding="utf-8")
        (task_dir / "progress.md").write_text("## Next step\nfinish health\n", encoding="utf-8")
        active_path.write_text(
            json.dumps(
                {
                    "task_id": task,
                    "title": "health",
                    "lane": "FAST",
                    "status": "IMPLEMENTING",
                    "allowed_paths": ["docs/governance/CURSOR-LEAN-HARNESS-MANUAL-SETUP.md"],
                    "approval_required": False,
                    "verify_commands": ["./tools/agent/verify-fast"],
                }
            ),
            encoding="utf-8",
        )

        # 4 FAST allowed path without approval
        r = run_hook(
            SCOPE,
            {
                "tool_name": "Write",
                "tool_input": {
                    "path": "docs/governance/CURSOR-LEAN-HARNESS-MANUAL-SETUP.md",
                    "contents": "x",
                },
            },
        )
        expect("fast-allow", r.get("permission") == "allow", str(r))

        # 5 STANDARD without approval blocked
        active_path.write_text(
            json.dumps(
                {
                    "task_id": "HEALTH-STD",
                    "title": "health",
                    "lane": "STANDARD",
                    "status": "IMPLEMENTING",
                    "allowed_paths": ["docs/governance/CURSOR-LEAN-HARNESS-MANUAL-SETUP.md"],
                    "approval_required": True,
                    "verify_commands": ["./tools/agent/verify-fast"],
                }
            ),
            encoding="utf-8",
        )
        std_dir = REPO / "work" / "active" / "HEALTH-STD"
        std_dir.mkdir(parents=True, exist_ok=True)
        (std_dir / "plan.md").write_text("# plan\n", encoding="utf-8")
        r = run_hook(
            SCOPE,
            {
                "tool_name": "Write",
                "tool_input": {
                    "path": "docs/governance/CURSOR-LEAN-HARNESS-MANUAL-SETUP.md",
                    "contents": "x",
                },
            },
        )
        expect("standard-without-approval-deny", r.get("permission") == "deny", str(r))

        # 6 STANDARD with approval passes
        appr = REPO / "work" / "approvals" / "HEALTH-STD.approved"
        appr.write_text("HUMAN APPROVED synthetic health check\n", encoding="utf-8")
        r = run_hook(
            SCOPE,
            {
                "tool_name": "Write",
                "tool_input": {
                    "path": "docs/governance/CURSOR-LEAN-HARNESS-MANUAL-SETUP.md",
                    "contents": "x",
                },
            },
        )
        expect("standard-with-approval-allow", r.get("permission") == "allow", str(r))

        # 7 outside allowed_paths blocked
        r = run_hook(
            SCOPE,
            {
                "tool_name": "Write",
                "tool_input": {"path": "backend/pom.xml", "contents": "x"},
            },
        )
        expect("outside-scope-deny", r.get("permission") == "deny", str(r))

        # 8 protected javadoc blocked
        r = run_hook(
            SCOPE,
            {
                "tool_name": "Write",
                "tool_input": {
                    "path": "build/generated-spring-modulith/javadoc.json",
                    "contents": "{}",
                },
            },
        )
        expect("javadoc-deny", r.get("permission") == "deny", str(r))

        # 9 checkpoint does not request follow-up
        r = run_hook(CHECK, {"hook_event_name": "stop"})
        expect("checkpoint-no-followup", "followup_message" not in r, str(r))
        r = run_hook(CHECK, {"hook_event_name": "preCompact"})
        expect(
            "checkpoint-precompact-no-followup",
            "followup_message" not in r,
            str(r),
        )
    finally:
        # cleanup synthetic files without rm -rf of workspace roots
        for rel in (
            "work/active/HEALTH-FAST/plan.md",
            "work/active/HEALTH-FAST/progress.md",
            "work/active/HEALTH-STD/plan.md",
            "work/approvals/HEALTH-STD.approved",
        ):
            path = REPO / rel
            if path.is_file():
                path.unlink()
        for drel in ("work/active/HEALTH-FAST", "work/active/HEALTH-STD"):
            d = REPO / drel
            if d.is_dir():
                try:
                    d.rmdir()
                except OSError:
                    pass
        if had_active and previous is not None:
            active_path.write_text(previous, encoding="utf-8")
        elif active_path.is_file():
            active_path.unlink()

    print("HEALTH_CHECK: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

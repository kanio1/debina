#!/usr/bin/env python3
"""After-shell check: detect unauthorized working-tree mutations. Never auto-restores."""
from __future__ import annotations

import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from agent_policy.active_state import load_active  # noqa: E402
from agent_policy.shell_policy import (  # noqa: E402
    git_porcelain,
    state_dir,
    unauthorized_new_paths,
)


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        payload = {}

    command = str(payload.get("command") or "")
    if not command:
        tin = payload.get("tool_input") or {}
        if isinstance(tin, dict):
            command = str(tin.get("command") or "")

    snap_path = state_dir(REPO) / "shell-pre.json"
    # Without a pre-command snapshot, do not attribute pre-existing dirty files.
    if not snap_path.is_file():
        print(json.dumps({}))
        return 0

    try:
        data = json.loads(snap_path.read_text(encoding="utf-8"))
        before = set(data.get("paths") or [])
    except json.JSONDecodeError:
        before = set()
        snap_path.unlink(missing_ok=True)
        print(json.dumps({}))
        return 0

    after = git_porcelain(REPO)
    state = load_active(REPO)
    bad = unauthorized_new_paths(before, after, state=state, repo=REPO)

    snap_path.unlink(missing_ok=True)

    flag = state_dir(REPO) / "UNAUTHORIZED_SHELL_WRITE_DETECTED"
    if bad:
        flag.write_text(
            json.dumps(
                {
                    "result": "UNAUTHORIZED_SHELL_WRITE_DETECTED",
                    "command": command,
                    "paths": bad,
                },
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        msg = (
            "UNAUTHORIZED_SHELL_WRITE_DETECTED\n"
            + "\n".join(f"- {p}" for p in bad)
            + "\nDo not auto-revert the working tree. Stop and request human review."
        )
        print(json.dumps({"additional_context": msg}))
        print(msg, file=sys.stderr)
        return 0

    if flag.is_file():
        flag.unlink()
    print(json.dumps({}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

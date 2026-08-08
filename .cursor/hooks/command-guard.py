#!/usr/bin/env python3
"""Block dangerous and unauthorized shell writes for Debina lean harness."""
from __future__ import annotations

import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from agent_policy.active_state import load_active  # noqa: E402
from agent_policy.shell_policy import classify_shell_command, git_porcelain, state_dir  # noqa: E402


def respond(permission: str, user_message: str = "", agent_message: str = "") -> None:
    out = {"permission": permission}
    if user_message:
        out["user_message"] = user_message
    if agent_message:
        out["agent_message"] = agent_message
    print(json.dumps(out))


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        respond("ask", "Invalid hook payload", "command-guard could not parse stdin JSON")
        return 0

    command = str(payload.get("command") or payload.get("tool_input", {}).get("command") or "")
    state = load_active(REPO)
    perm, reason = classify_shell_command(command, state=state, repo=REPO)
    if perm == "allow":
        snap = state_dir(REPO) / "shell-pre.json"
        snap.write_text(
            json.dumps({"command": command, "paths": sorted(git_porcelain(REPO))}),
            encoding="utf-8",
        )
    respond(perm, reason if perm != "allow" else "", reason if perm != "allow" else "")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

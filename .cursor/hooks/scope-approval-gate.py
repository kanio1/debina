#!/usr/bin/env python3
"""Gate write/edit/delete tools using phased approval policy."""
from __future__ import annotations

import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from agent_policy.active_state import load_active  # noqa: E402
from agent_policy.write_gate import evaluate_write, norm_path  # noqa: E402


def respond(permission: str, user_message: str = "", agent_message: str = "") -> None:
    out = {"permission": permission}
    if user_message:
        out["user_message"] = user_message
    if agent_message:
        out["agent_message"] = agent_message
    print(json.dumps(out))


def tool_paths(payload: dict) -> list[str]:
    tin = payload.get("tool_input") or payload.get("input") or {}
    paths: list[str] = []
    for key in ("path", "file_path", "target_notebook"):
        val = tin.get(key)
        if isinstance(val, str) and val:
            paths.append(val)
    return paths


def tool_contents(payload: dict) -> str | None:
    tin = payload.get("tool_input") or payload.get("input") or {}
    for key in ("contents", "new_string", "content"):
        val = tin.get(key)
        if isinstance(val, str):
            return val
    return None


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        respond("ask", "Invalid hook payload", "scope-approval-gate could not parse JSON")
        return 0

    tool = str(payload.get("tool_name") or payload.get("tool") or "")
    paths = [norm_path(p, REPO) for p in tool_paths(payload)]
    if not paths:
        respond("ask", "Write tool without path", "scope-approval-gate needs a path")
        return 0

    state = load_active(REPO)
    contents = tool_contents(payload)

    for p in paths:
        perm, reason = evaluate_write(
            p,
            tool=tool,
            state=state,
            repo=REPO,
            contents=contents if p == "work/ACTIVE.json" else None,
        )
        if perm != "allow":
            respond(perm if perm in {"deny", "ask"} else "deny", reason, reason)
            return 0

    # Optional explicit delete markers for non-work deletes remain soft for work/**
    respond("allow")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Gate write/edit/delete tools on ACTIVE.json lane, scope, and approvals."""
from __future__ import annotations

import fnmatch
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
ACTIVE = REPO / "work" / "ACTIVE.json"
PROTECTED = "build/generated-spring-modulith/javadoc.json"


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


def norm(path: str) -> str:
    p = path.replace("\\", "/")
    if p.startswith("./"):
        p = p[2:]
    abs_repo = str(REPO) + "/"
    if p.startswith(abs_repo):
        p = p[len(abs_repo) :]
    return p.lstrip("/")


def path_allowed(path: str, patterns: list[str]) -> bool:
    n = norm(path)
    for pat in patterns:
        if pat.endswith("/**"):
            prefix = pat[:-3]
            if n == prefix.rstrip("/") or n.startswith(prefix):
                return True
        elif "*" in pat:
            if fnmatch.fnmatch(n, pat):
                return True
        elif n == pat or n.startswith(pat.rstrip("/") + "/"):
            return True
    return False


def approval_ok(task_id: str, lane: str) -> tuple[bool, str]:
    approvals = REPO / "work" / "approvals"
    if lane == "FAST":
        return True, ""
    if lane == "STANDARD":
        f = approvals / f"{task_id}.approved"
        if f.is_file() and f.stat().st_size > 0:
            return True, ""
        return False, f"STANDARD requires human file work/approvals/{task_id}.approved"
    if lane == "DECISION":
        dec = approvals / f"{task_id}.decision.approved"
        impl = approvals / f"{task_id}.implementation.approved"
        if dec.is_file() and impl.is_file():
            return True, ""
        return False, (
            f"DECISION requires {dec.relative_to(REPO)} and {impl.relative_to(REPO)}"
        )
    return False, f"unknown lane {lane}"


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        respond("ask", "Invalid hook payload", "scope-approval-gate could not parse JSON")
        return 0

    tool = str(payload.get("tool_name") or payload.get("tool") or "")
    paths = [norm(p) for p in tool_paths(payload)]
    if not paths:
        respond("ask", "Write tool without path", "scope-approval-gate needs a path")
        return 0

    for p in paths:
        if p == PROTECTED:
            respond("deny", "Protected generated javadoc", f"blocked write to {PROTECTED}")
            return 0

    is_delete = "Delete" in tool

    if not ACTIVE.is_file():
        for p in paths:
            if not p.startswith("work/"):
                respond(
                    "deny",
                    "No work/ACTIVE.json — only work/** discovery writes allowed",
                    f"blocked write outside work/: {p}",
                )
                return 0
        respond("allow")
        return 0

    try:
        active = json.loads(ACTIVE.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        respond("deny", "ACTIVE.json is invalid JSON", "fix work/ACTIVE.json")
        return 0

    task_id = str(active.get("task_id") or "")
    lane = str(active.get("lane") or "")
    allowed_paths = [str(x) for x in (active.get("allowed_paths") or [])]
    verify_commands = active.get("verify_commands") or []

    if not task_id or not lane or not allowed_paths:
        respond("deny", "ACTIVE.json missing task_id/lane/allowed_paths", "complete ACTIVE.json")
        return 0

    plan = REPO / "work" / "active" / task_id / "plan.md"
    if lane == "FAST":
        if not plan.is_file():
            respond("deny", "FAST requires plan.md for the active task", f"missing {plan}")
            return 0
        if not verify_commands:
            respond("deny", "FAST requires at least one verify_commands entry", "add verify_commands")
            return 0

    ok, reason = approval_ok(task_id, lane)
    if not ok:
        respond("deny", reason, reason)
        return 0

    plan_text = plan.read_text(encoding="utf-8") if plan.is_file() else ""
    overlay = [
        f"work/active/{task_id}/**",
        "work/QUEUE.md",
        "work/ACTIVE.json",
        "HANDOFF.md",
    ]

    for p in paths:
        if path_allowed(p, allowed_paths + overlay):
            if is_delete:
                marker = f"delete:{p}"
                compact = (plan_text + "\n" + json.dumps(active)).replace(" ", "")
                if marker not in compact and f"delete {p}" not in (plan_text + "\n" + json.dumps(active)):
                    respond(
                        "deny",
                        f"Deletion of {p} must be explicit in plan/ACTIVE and approved",
                        "explicit delete scope required",
                    )
                    return 0
            continue
        respond("deny", f"Path outside allowed_paths: {p}", f"blocked {p}")
        return 0

    respond("allow")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

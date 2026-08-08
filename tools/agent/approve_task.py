#!/usr/bin/env python3
"""Mark ACTIVE approval_state after verifying human approval markers exist."""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from agent_policy.active_state import (  # noqa: E402
    WRAPPER_ENV,
    active_path,
    implementation_approved,
    load_active,
)


def main() -> int:
    os.environ[WRAPPER_ENV] = "1"
    ap = argparse.ArgumentParser()
    ap.add_argument("--task-id", default=None)
    args = ap.parse_args()

    state = load_active(REPO)
    if state is None:
        print("approve-task: no ACTIVE.json", file=sys.stderr)
        return 2
    tid = args.task_id or state.task_id
    if tid != state.task_id:
        print("approve-task: task id mismatch", file=sys.stderr)
        return 2
    if not implementation_approved(state.policy_profile, tid, REPO):
        print(
            "approve-task: human approval marker missing — "
            f"create work/approvals/{tid}.approved (agents must not forge)",
            file=sys.stderr,
        )
        return 3

    data = dict(state.raw)
    data["approval_state"] = "APPROVED"
    data["approval_required"] = True
    if data.get("phase") in {"ANALYZING", "SPEC_READY", "BOOTSTRAP"}:
        data["phase"] = "IMPLEMENTING"
        data["write_scope"] = "IMPLEMENTATION"
        data["status"] = "IMPLEMENTING"
    active_path(REPO).write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    print(f"approve-task: {tid} approval_state=APPROVED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

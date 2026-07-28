#!/usr/bin/env python3
"""Remind about progress/HANDOFF at compact/stop without restarting the agent."""
from __future__ import annotations

import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
ACTIVE = REPO / "work" / "ACTIVE.json"


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        payload = {}

    event = str(payload.get("hook_event_name") or payload.get("event") or "")
    lines = [
        "Lean harness checkpoint:",
        "- Update work/active/<TASK>/progress.md if steps advanced.",
    ]

    material = False
    next_step = "none recorded"
    verify_warning = ""
    if ACTIVE.is_file():
        try:
            active = json.loads(ACTIVE.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            active = {}
        status = str(active.get("status") or "")
        lane = str(active.get("lane") or "")
        task = str(active.get("task_id") or "")
        material = status in {"IMPLEMENTING", "VERIFYING", "SPEC_READY", "READY", "BLOCKED"} or bool(
            active.get("allowed_paths")
        )
        progress = REPO / "work" / "active" / task / "progress.md"
        if progress.is_file():
            text = progress.read_text(encoding="utf-8")
            for line in text.splitlines():
                if line.lower().startswith("## next step"):
                    next_step = "see progress.md Next step"
                    break
            if "PASS" not in text and "verify" in text.lower() and status in {"IMPLEMENTING", "VERIFYING"}:
                verify_warning = "Verify may be incomplete — check progress.md / run ./tools/agent/verify-task"
        lines.append(f"- Active task: {task} lane={lane} status={status}")
        lines.append(f"- First next step: {next_step}")
        if material:
            lines.append("- If this session was material, refresh HANDOFF.md via session-handoff.")
        else:
            lines.append("- Skip HANDOFF.md unless the session was material.")
        if verify_warning:
            lines.append(f"- WARNING: {verify_warning}")
    else:
        lines.append("- No work/ACTIVE.json; skip HANDOFF unless project state changed.")

    lines.append("- Do not auto-implement, auto-commit, or resume a follow-up agent loop.")

    # Never return followup_message — that can re-invoke the agent on stop.
    context = "\n".join(lines)
    if event.lower() == "precompact" or "compact" in event.lower():
        print(json.dumps({"additional_context": context}))
    else:
        # stop: observe only
        print(json.dumps({}))
        print(context, file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

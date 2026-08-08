#!/usr/bin/env python3
"""Atomic task bootstrap for Debina lean harness."""
from __future__ import annotations

import argparse
import json
import re
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from agent_policy.active_state import (  # noqa: E402
    APPROVAL_STATES,
    PHASES,
    POLICY_PROFILES,
    WRITE_SCOPES,
    WRAPPER_ENV,
    active_path,
)

TASK_RE = re.compile(r"^[A-Z0-9][A-Z0-9_-]{1,80}$")


def die(msg: str, code: int = 2) -> None:
    print(f"bootstrap-task: {msg}", file=sys.stderr)
    raise SystemExit(code)


def update_queue_now(queue_path: Path, task_id: str) -> str:
    text = queue_path.read_text(encoding="utf-8") if queue_path.is_file() else "NOW\n\nNEXT\n\n"
    lines = text.splitlines()
    out: list[str] = []
    section = None
    now_written = False
    for line in lines:
        if line.strip() in {"NOW", "NEXT", "LATER", "BLOCKED", "RECENTLY_COMPLETED"}:
            section = line.strip()
            out.append(line)
            if section == "NOW" and not now_written:
                out.append(f"- {task_id}")
                now_written = True
            continue
        if section == "NOW":
            # drop previous NOW entries
            if line.startswith("- "):
                continue
            out.append(line)
            continue
        out.append(line)
    if not now_written:
        if "NOW" not in {l.strip() for l in out}:
            out = ["NOW", f"- {task_id}", ""] + out
        else:
            # insert after NOW header
            rebuilt: list[str] = []
            inserted = False
            for line in out:
                rebuilt.append(line)
                if line.strip() == "NOW" and not inserted:
                    rebuilt.append(f"- {task_id}")
                    inserted = True
            out = rebuilt
    return "\n".join(out).rstrip() + "\n"


def main() -> int:
    import os

    os.environ[WRAPPER_ENV] = "1"
    ap = argparse.ArgumentParser(description="Bootstrap Debina ACTIVE task atomically")
    ap.add_argument("--task-id", required=True)
    ap.add_argument("--title", required=True)
    ap.add_argument("--policy-profile", required=True, choices=POLICY_PROFILES)
    ap.add_argument("--phase", default="ANALYZING", choices=PHASES)
    ap.add_argument("--approval-state", default=None, choices=APPROVAL_STATES)
    ap.add_argument("--write-scope", default=None, choices=WRITE_SCOPES)
    ap.add_argument("--allowed-path", action="append", default=[])
    ap.add_argument("--verify-command", action="append", default=["./tools/agent/verify-fast"])
    args = ap.parse_args()

    if not TASK_RE.match(args.task_id):
        die("invalid task id")

    active = active_path(REPO)
    if active.is_file():
        die("ACTIVE.json already exists — closeout first", 3)

    profile = args.policy_profile
    phase = args.phase
    write_scope = args.write_scope or (
        "WORK_ONLY" if phase in {"BOOTSTRAP", "ANALYZING", "SPEC_READY"} else "IMPLEMENTATION"
    )
    if args.approval_state:
        approval_state = args.approval_state
    elif profile == "FAST":
        approval_state = "NOT_REQUIRED"
    elif write_scope == "WORK_ONLY":
        approval_state = "NOT_REQUIRED"
    else:
        approval_state = "PENDING"

    allowed = list(args.allowed_path)
    task_glob = f"work/active/{args.task_id}/**"
    if task_glob not in allowed:
        allowed.append(task_glob)

    payload = {
        "task_id": args.task_id,
        "title": args.title,
        "lane": profile,  # backward-compat mirror only
        "policy_profile": profile,
        "phase": phase,
        "approval_state": approval_state,
        "write_scope": write_scope,
        "status": phase if phase != "ANALYZING" else "ACTIVE",
        "allowed_paths": allowed,
        "approval_required": profile in {"STANDARD", "DECISION"} and write_scope == "IMPLEMENTATION",
        "verify_commands": args.verify_command,
    }

    task_dir = REPO / "work" / "active" / args.task_id
    queue_path = REPO / "work" / "QUEUE.md"

    tmp = Path(tempfile.mkdtemp(prefix="debina-bootstrap-"))
    try:
        (tmp / "plan.md").write_text(
            f"# {args.task_id} — Plan\n\n## Classification\n\n"
            f"- policy_profile: {profile}\n"
            f"- phase: {phase}\n"
            f"- approval_state: {approval_state}\n"
            f"- write_scope: {write_scope}\n",
            encoding="utf-8",
        )
        (tmp / "progress.md").write_text(
            f"# Progress — {args.task_id}\n\n## Status\n\n`{phase}`\n\n"
            "## Completed\n\n- Bootstrapped via tools/agent/bootstrap-task\n\n"
            "## Next step\n\nContinue analysis or await approval.\n",
            encoding="utf-8",
        )
        (tmp / "ACTIVE.json").write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        (tmp / "QUEUE.md").write_text(update_queue_now(queue_path, args.task_id), encoding="utf-8")

        task_dir.mkdir(parents=True, exist_ok=True)
        (task_dir / "plan.md").write_text((tmp / "plan.md").read_text(encoding="utf-8"), encoding="utf-8")
        (task_dir / "progress.md").write_text((tmp / "progress.md").read_text(encoding="utf-8"), encoding="utf-8")
        queue_path.write_text((tmp / "QUEUE.md").read_text(encoding="utf-8"), encoding="utf-8")
        # ACTIVE last — failure before this leaves no ACTIVE
        active.write_text((tmp / "ACTIVE.json").read_text(encoding="utf-8"), encoding="utf-8")
    except Exception:
        if active.is_file():
            active.unlink()
        raise
    finally:
        for p in tmp.iterdir():
            p.unlink()
        tmp.rmdir()

    print(f"bootstrap-task: created {args.task_id} profile={profile} phase={phase}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

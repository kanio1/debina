#!/usr/bin/env python3
"""Archive active task directory and clear ACTIVE.json."""
from __future__ import annotations

import argparse
import os
import shutil
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from agent_policy.active_state import WRAPPER_ENV, active_path, load_active  # noqa: E402


def main() -> int:
    os.environ[WRAPPER_ENV] = "1"
    ap = argparse.ArgumentParser()
    ap.add_argument("--verdict", default="PASS")
    ap.add_argument("--next-task", default="")
    args = ap.parse_args()

    state = load_active(REPO)
    if state is None:
        print("closeout-task: no ACTIVE.json", file=sys.stderr)
        return 2

    src = REPO / "work" / "active" / state.task_id
    dst = REPO / "work" / "archive" / state.task_id
    if src.is_dir():
        dst.parent.mkdir(parents=True, exist_ok=True)
        if dst.exists():
            print(f"closeout-task: archive already exists: {dst}", file=sys.stderr)
            return 3
        shutil.copytree(src, dst)

    queue = REPO / "work" / "QUEUE.md"
    if queue.is_file():
        lines = queue.read_text(encoding="utf-8").splitlines()
        out: list[str] = []
        section = None
        for line in lines:
            if line.strip() in {"NOW", "NEXT", "LATER", "BLOCKED", "RECENTLY_COMPLETED"}:
                section = line.strip()
                out.append(line)
                if section == "RECENTLY_COMPLETED":
                    out.append(f"- {state.task_id} — {args.verdict}")
                continue
            if section == "NOW" and line.startswith("- "):
                continue
            if section == "NEXT" and args.next_task and line.startswith("- "):
                continue
            out.append(line)
        if args.next_task:
            rebuilt: list[str] = []
            for line in out:
                rebuilt.append(line)
                if line.strip() == "NEXT":
                    rebuilt.append(f"- {args.next_task}")
            out = rebuilt
        # promote NEXT to NOW if empty NOW
        text = "\n".join(out) + "\n"
        if args.next_task:
            text = text.replace("NOW\n\n", f"NOW\n- {args.next_task}\n\n", 1)
            # remove duplicate from NEXT
            qlines = []
            section = None
            seen_now = False
            for line in text.splitlines():
                if line.strip() in {"NOW", "NEXT", "LATER", "BLOCKED", "RECENTLY_COMPLETED"}:
                    section = line.strip()
                    qlines.append(line)
                    continue
                if section == "NOW" and line.strip() == f"- {args.next_task}":
                    if seen_now:
                        continue
                    seen_now = True
                if section == "NEXT" and line.strip() == f"- {args.next_task}":
                    continue
                qlines.append(line)
            text = "\n".join(qlines) + "\n"
        queue.write_text(text, encoding="utf-8")

    if src.is_dir():
        shutil.rmtree(src)
    active_path(REPO).unlink()
    print(f"closeout-task: archived {state.task_id} verdict={args.verdict}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

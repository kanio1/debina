#!/usr/bin/env python3
"""Controlled promotion of Phase A mirror Spec Kit feature to canonical paths."""
from __future__ import annotations

import argparse
import json
import os
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from agent_policy.active_state import WRAPPER_ENV, active_path, load_active  # noqa: E402
from agent_policy.spec_kit import (  # noqa: E402
    canonical_feature_json_for_binding,
    collect_tree_hashes,
    parse_binding_from_mirror,
    promote_mirror_to_canonical,
    mirror_pointer_path,
    load_json,
)


def die(msg: str, code: int = 2) -> None:
    print(f"promote-spec-kit-feature: {msg}", file=sys.stderr)
    raise SystemExit(code)


def bind_active_feature(task_id: str, binding) -> None:
    active = active_path(REPO)
    data = json.loads(active.read_text(encoding="utf-8"))
    if data.get("task_id") != task_id:
        die("ACTIVE task_id mismatch")
    data["spec_kit"] = {
        "feature_id": binding.feature_id,
        "feature_number": binding.feature_number,
        "feature_slug": binding.feature_slug,
        "feature_directory": binding.feature_directory,
        "feature_state_path": ".specify/feature.json",
        "mirror_feature_directory": binding.mirror_feature_directory,
    }
    active.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    os.environ[WRAPPER_ENV] = "1"
    ap = argparse.ArgumentParser(description="Promote bound mirror Spec Kit feature to canonical paths")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    state = load_active(REPO)
    if state is None:
        die("no ACTIVE.json")
    task_id = state.task_id
    if not task_id:
        die("ACTIVE missing task_id")

    if state.phase != "ANALYZING":
        die(f"promotion requires phase=ANALYZING (current={state.phase})")
    if state.write_scope != "SPECIFICATION":
        die(f"promotion requires write_scope=SPECIFICATION (current={state.write_scope})")
    if state.approval_state != "PENDING":
        die(f"promotion requires approval_state=PENDING (current={state.approval_state})")

    mirror_meta = load_json(mirror_pointer_path(REPO, task_id))
    if not mirror_meta:
        die("missing mirror .specify-feature.json")
    binding = parse_binding_from_mirror(mirror_meta, task_id)
    if not binding:
        die("invalid mirror binding metadata")

    ok, reason = promote_mirror_to_canonical(REPO, task_id, dry_run=args.dry_run)
    if not ok:
        die(reason, 3)

    if not args.dry_run:
        bind_active_feature(task_id, binding)

    print(f"promote-spec-kit-feature: {reason} task={task_id} feature={binding.feature_directory}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Controlled ACTIVE state mutations (phase, scope, paths, profile, spec-kit binding)."""
from __future__ import annotations

import argparse
import json
import os
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from agent_policy.active_state import (  # noqa: E402
    PHASES,
    POLICY_PROFILES,
    PROFILE_RANK,
    WRITE_SCOPES,
    WRAPPER_ENV,
    active_path,
    load_active,
    validate_state_combination,
)
from agent_policy.spec_kit import (  # noqa: E402
    validate_active_spec_kit_mutation,
    validate_feature_directory,
    validate_feature_id,
    validate_feature_slug,
    validate_spec_kit_fields,
)


def die(msg: str, code: int = 2) -> None:
    print(f"set-task-state: {msg}", file=sys.stderr)
    raise SystemExit(code)


def append_progress(task_id: str, line: str) -> None:
    progress = REPO / "work" / "active" / task_id / "progress.md"
    if progress.is_file():
        progress.write_text(progress.read_text(encoding="utf-8") + line, encoding="utf-8")


def main() -> int:
    os.environ[WRAPPER_ENV] = "1"
    ap = argparse.ArgumentParser()
    ap.add_argument("--phase", choices=PHASES)
    ap.add_argument("--write-scope", choices=WRITE_SCOPES)
    ap.add_argument("--approval-state", choices=["NOT_REQUIRED", "PENDING", "APPROVED", "REJECTED"])
    ap.add_argument("--policy-profile", choices=POLICY_PROFILES)
    ap.add_argument("--allow-downgrade", action="store_true")
    ap.add_argument("--downgrade-reason", default="")
    ap.add_argument("--add-allowed-path", action="append", default=[])
    ap.add_argument("--set-allowed-paths", default=None, help="Comma-separated replacement list")
    ap.add_argument("--add-artifact-path", action="append", default=[])
    ap.add_argument("--set-artifact-paths", default=None)
    ap.add_argument("--add-implementation-path", action="append", default=[])
    ap.add_argument("--set-implementation-paths", default=None)
    ap.add_argument("--spec-kit-feature-id")
    ap.add_argument("--spec-kit-feature-slug")
    ap.add_argument("--spec-kit-feature-directory")
    ap.add_argument("--clear-spec-kit", action="store_true")
    ap.add_argument("--blocker", default=None)
    ap.add_argument("--clear-blocker", action="store_true")
    ap.add_argument("--status")
    ap.add_argument("--note", default="")
    ap.add_argument("--reason", default="")
    args = ap.parse_args()

    state = load_active(REPO)
    if state is None:
        die("no ACTIVE.json")

    data = dict(state.raw)
    task_id = state.task_id

    if args.policy_profile:
        new_rank = PROFILE_RANK[args.policy_profile]
        cur_rank = PROFILE_RANK[state.policy_profile]
        if new_rank < cur_rank:
            if not args.allow_downgrade or not args.downgrade_reason.strip():
                die("profile downgrade requires --allow-downgrade and --downgrade-reason", 3)
            os.environ["DEBINA_ALLOW_PROFILE_DOWNGRADE"] = "1"
            stamp = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%MZ")
            append_progress(
                task_id,
                f"\n- {stamp} PROFILE_DOWNGRADE {state.policy_profile}→{args.policy_profile}: "
                f"{args.downgrade_reason.strip()}\n",
            )
        data["policy_profile"] = args.policy_profile
        data["lane"] = args.policy_profile

    if args.phase:
        data["phase"] = args.phase
    if args.write_scope:
        data["write_scope"] = args.write_scope
    if args.approval_state:
        data["approval_state"] = args.approval_state
    if args.status:
        data["status"] = args.status
    if args.set_allowed_paths is not None:
        data["allowed_paths"] = [p.strip() for p in args.set_allowed_paths.split(",") if p.strip()]
    for p in args.add_allowed_path:
        paths = list(data.get("allowed_paths") or [])
        if p not in paths:
            paths.append(p)
        data["allowed_paths"] = paths
    if args.set_artifact_paths is not None:
        data["artifact_paths"] = [p.strip() for p in args.set_artifact_paths.split(",") if p.strip()]
    for p in args.add_artifact_path:
        paths = list(data.get("artifact_paths") or [])
        if p not in paths:
            paths.append(p)
        data["artifact_paths"] = paths
    if args.set_implementation_paths is not None:
        data["implementation_paths"] = [
            p.strip() for p in args.set_implementation_paths.split(",") if p.strip()
        ]
    for p in args.add_implementation_path:
        paths = list(data.get("implementation_paths") or [])
        if p not in paths:
            paths.append(p)
        data["implementation_paths"] = paths

    if args.clear_spec_kit:
        data.pop("spec_kit", None)
    elif any(
        x is not None
        for x in (
            args.spec_kit_feature_id,
            args.spec_kit_feature_slug,
            args.spec_kit_feature_directory,
        )
    ):
        sk = dict(data.get("spec_kit") or {})
        if args.spec_kit_feature_id:
            ok, reason = validate_feature_id(args.spec_kit_feature_id)
            if not ok:
                die(reason)
            sk["feature_id"] = args.spec_kit_feature_id
            sk["feature_number"] = args.spec_kit_feature_id.split("-", 1)[0]
        if args.spec_kit_feature_slug:
            ok, reason = validate_feature_slug(args.spec_kit_feature_slug)
            if not ok:
                die(reason)
            sk["feature_slug"] = args.spec_kit_feature_slug
        if args.spec_kit_feature_directory:
            ok, reason = validate_feature_directory(args.spec_kit_feature_directory)
            if not ok:
                die(reason)
            sk["feature_directory"] = args.spec_kit_feature_directory
        sk["feature_state_path"] = ".specify/feature.json"
        fid = str(sk.get("feature_id") or "")
        fslug = str(sk.get("feature_slug") or "")
        fdir = sk.get("feature_directory")
        if fid and fslug and fdir:
            ok, reason = validate_spec_kit_fields(
                feature_id=fid, feature_slug=fslug, feature_directory=str(fdir)
            )
            if not ok:
                die(reason)
        data["spec_kit"] = sk

    if args.clear_blocker:
        data.pop("blocker", None)
    elif args.blocker is not None:
        data["blocker"] = args.blocker

    reason = args.reason.strip() or args.note.strip()
    if reason:
        notes = str(data.get("notes") or "")
        data["notes"] = (notes + "\n" + reason).strip()
        stamp = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%MZ")
        append_progress(task_id, f"\n- {stamp} STATE_UPDATE: {reason}\n")

    ok, reason = validate_active_spec_kit_mutation(state.raw, data)
    if not ok:
        die(reason, 4)

    profile = str(data.get("policy_profile") or state.policy_profile)
    phase = str(data.get("phase") or state.phase)
    approval = str(data.get("approval_state") or state.approval_state)
    scope = str(data.get("write_scope") or state.write_scope)
    combo_ok, combo_reason = validate_state_combination(
        profile, phase, approval, scope, task_id, data, REPO
    )
    if not combo_ok:
        die(combo_reason, 5)

    target = active_path(REPO)
    tmp = Path(tempfile.mkdtemp(prefix="debina-active-", dir=str(REPO / "work")))
    backup = tmp / "ACTIVE.json.bak"
    try:
        if target.is_file():
            backup.write_text(target.read_text(encoding="utf-8"), encoding="utf-8")
        target.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    except OSError as exc:
        if backup.is_file():
            target.write_text(backup.read_text(encoding="utf-8"), encoding="utf-8")
        die(f"atomic write failed: {exc}", 6)
    finally:
        import shutil

        shutil.rmtree(tmp, ignore_errors=True)

    print(
        "set-task-state: "
        f"profile={data.get('policy_profile')} phase={data.get('phase')} "
        f"approval={data.get('approval_state')} scope={data.get('write_scope')}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

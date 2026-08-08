"""ACTIVE.json normalization and approval helpers."""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any

MIRROR_POINTER_NAME = ".specify-feature.json"

POLICY_PROFILES = ("FAST", "STANDARD", "DECISION")
PHASES = (
    "BOOTSTRAP",
    "ANALYZING",
    "SPEC_READY",
    "IMPLEMENTING",
    "VERIFYING",
    "REVIEWING",
    "CLOSING",
    "BLOCKED",
    "COMPLETED",
)
APPROVAL_STATES = ("NOT_REQUIRED", "PENDING", "APPROVED", "REJECTED")
WRITE_SCOPES = ("WORK_ONLY", "SPECIFICATION", "IMPLEMENTATION", "REVIEW", "CLOSEOUT")
WRAPPER_ENV = "DEBINA_AGENT_WRAPPER"
PROFILE_RANK = {"FAST": 1, "STANDARD": 2, "DECISION": 3}


@dataclass
class ActiveState:
    task_id: str
    policy_profile: str
    phase: str
    approval_state: str
    write_scope: str
    allowed_paths: list[str]
    lane: str
    status: str
    verify_commands: list[str]
    raw: dict[str, Any]
    anomaly_flags: list[str]

    @property
    def task_work_glob(self) -> str:
        return f"work/active/{self.task_id}/**"


def repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def active_path(repo: Path | None = None) -> Path:
    return (repo or repo_root()) / "work" / "ACTIVE.json"


def load_active(repo: Path | None = None) -> ActiveState | None:
    path = active_path(repo)
    if not path.is_file():
        return None
    data = json.loads(path.read_text(encoding="utf-8"))
    return normalize_active(data, repo or repo_root())


def wrapper_trusted() -> bool:
    return os.environ.get(WRAPPER_ENV) == "1"


def implementation_approved(profile: str, task_id: str, repo: Path) -> bool:
    approvals = repo / "work" / "approvals"
    if profile == "FAST":
        return True
    if profile == "STANDARD":
        f = approvals / f"{task_id}.approved"
        return f.is_file() and f.stat().st_size > 0
    if profile == "DECISION":
        d = approvals / f"{task_id}.decision.approved"
        i = approvals / f"{task_id}.implementation.approved"
        return (
            d.is_file()
            and i.is_file()
            and d.stat().st_size > 0
            and i.stat().st_size > 0
        )
    return False


def _phase_from_status(status: str) -> str:
    mapping = {
        "DISCOVERY": "ANALYZING",
        "ACTIVE": "ANALYZING",
        "SPEC_READY": "SPEC_READY",
        "READY": "SPEC_READY",
        "IMPLEMENTING": "IMPLEMENTING",
        "VERIFYING": "VERIFYING",
        "DONE": "COMPLETED",
        "BLOCKED": "BLOCKED",
    }
    return mapping.get(status, "ANALYZING")


def _scope_from_phase(phase: str) -> str:
    if phase in {"BOOTSTRAP", "ANALYZING", "SPEC_READY", "BLOCKED"}:
        return "WORK_ONLY"
    if phase in {"IMPLEMENTING", "VERIFYING"}:
        return "IMPLEMENTATION"
    if phase == "REVIEWING":
        return "REVIEW"
    if phase in {"CLOSING", "COMPLETED"}:
        return "CLOSEOUT"
    return "WORK_ONLY"


def _derive_approval_state(
    profile: str, phase: str, write_scope: str, task_id: str, repo: Path
) -> str:
    if profile == "FAST":
        return "NOT_REQUIRED"
    if implementation_approved(profile, task_id, repo):
        return "APPROVED"
    if profile in {"STANDARD", "DECISION"}:
        if phase in {"BOOTSTRAP", "ANALYZING", "SPEC_READY", "BLOCKED"}:
            return "PENDING"
        if write_scope in {"WORK_ONLY", "SPECIFICATION", "REVIEW", "CLOSEOUT"}:
            return "PENDING"
        if phase in {"IMPLEMENTING", "VERIFYING"}:
            return "PENDING"
    return "PENDING"


def artifact_paths(raw: dict[str, Any]) -> list[str]:
    paths = raw.get("artifact_paths")
    if isinstance(paths, list) and paths:
        return [str(p) for p in paths]
    task_id = str(raw.get("task_id") or "").strip()
    out: list[str] = []
    if task_id:
        out.append(f"work/active/{task_id}/**")
    sk = raw.get("spec_kit") if isinstance(raw.get("spec_kit"), dict) else {}
    fdir = str(sk.get("feature_directory") or "").strip()
    if fdir:
        out.append(f"{fdir.rstrip('/')}/**")
        out.append(".specify/feature.json")
    return out


def implementation_paths(raw: dict[str, Any]) -> list[str]:
    paths = raw.get("implementation_paths")
    if isinstance(paths, list):
        return [str(p) for p in paths]
    return [
        str(p)
        for p in (raw.get("allowed_paths") or [])
        if "spec-kit-pilot" in str(p) or "tests/tools" in str(p)
    ]


def maintenance_paths(raw: dict[str, Any]) -> list[str]:
    paths = raw.get("maintenance_paths")
    if isinstance(paths, list):
        return [str(p) for p in paths]
    return []


def specification_scope(write_scope: str) -> bool:
    return write_scope == "SPECIFICATION"


def has_spec_kit_binding(raw: dict[str, Any], repo: Path, task_id: str) -> bool:
    sk = raw.get("spec_kit")
    if isinstance(sk, dict) and (sk.get("feature_directory") or sk.get("feature_slug")):
        return True
    mirror = repo / "work" / "active" / task_id / MIRROR_POINTER_NAME
    return mirror.is_file()


def validate_state_combination(
    profile: str,
    phase: str,
    approval_state: str,
    write_scope: str,
    task_id: str,
    raw: dict[str, Any],
    repo: Path | None = None,
) -> tuple[bool, str]:
    repo = repo or repo_root()
    if not task_id and approval_state == "APPROVED":
        return False, "APPROVED without active task"
    if phase == "ANALYZING" and write_scope == "IMPLEMENTATION":
        return False, "ANALYZING + IMPLEMENTATION scope invalid"
    if phase == "SPEC_READY" and write_scope == "SPECIFICATION":
        return False, "SPEC_READY + SPECIFICATION invalid"
    if phase == "IMPLEMENTING" and write_scope == "SPECIFICATION":
        return False, "IMPLEMENTING + SPECIFICATION invalid"
    if profile == "STANDARD" and phase == "IMPLEMENTING" and approval_state == "PENDING":
        return False, "STANDARD + IMPLEMENTING + PENDING invalid"
    if profile == "DECISION" and phase == "IMPLEMENTING" and approval_state == "PENDING":
        return False, "DECISION + IMPLEMENTING + PENDING invalid"
    if write_scope == "SPECIFICATION" and not has_spec_kit_binding(raw, repo, task_id):
        return False, "SPECIFICATION without bound or predeclared feature"
    if write_scope == "SPECIFICATION" and approval_state == "REJECTED":
        return False, "SPECIFICATION blocked when REJECTED"
    if (
        write_scope == "IMPLEMENTATION"
        and phase in {"IMPLEMENTING", "VERIFYING"}
        and profile in {"STANDARD", "DECISION"}
        and approval_state != "APPROVED"
        and not implementation_approved(profile, task_id, repo)
    ):
        return False, f"{profile} IMPLEMENTING requires APPROVED"
    if write_scope == "IMPLEMENTATION" and phase not in {"IMPLEMENTING", "VERIFYING"}:
        return False, f"{phase} + IMPLEMENTATION invalid"
    sk = raw.get("spec_kit")
    if isinstance(sk, dict) and sk.get("feature_directory"):
        from .spec_kit import validate_spec_kit_fields
        ok, reason = validate_spec_kit_fields(
            feature_id=str(sk.get("feature_id") or ""),
            feature_slug=str(sk.get("feature_slug") or ""),
            feature_directory=str(sk.get("feature_directory")),
        )
        if sk.get("feature_id") and sk.get("feature_slug") and not ok:
            return False, reason
    return True, "ok"


def normalize_active(data: dict[str, Any], repo: Path) -> ActiveState:
    flags: list[str] = []
    task_id = str(data.get("task_id") or "").strip()
    lane = str(data.get("lane") or "").strip().upper()
    profile = str(data.get("policy_profile") or "").strip().upper()
    if not profile:
        profile = lane if lane in POLICY_PROFILES else "FAST"
        flags.append("policy_profile_missing_derived_from_lane")
    if profile not in POLICY_PROFILES:
        flags.append(f"invalid_policy_profile:{profile}")
        profile = "STANDARD"

    if lane and lane in POLICY_PROFILES and PROFILE_RANK[lane] < PROFILE_RANK[profile]:
        flags.append("lane_lower_than_policy_profile")
    if lane == "FAST" and profile in {"STANDARD", "DECISION"}:
        flags.append("technical_fast_bypass")

    status = str(data.get("status") or "").strip().upper()
    phase = str(data.get("phase") or "").strip().upper()
    if not phase:
        phase = _phase_from_status(status)
        flags.append("phase_missing_derived_from_status")
    if phase not in PHASES:
        flags.append(f"invalid_phase:{phase}")
        phase = "ANALYZING"

    write_scope = str(data.get("write_scope") or "").strip().upper()
    if not write_scope:
        write_scope = _scope_from_phase(phase)
        flags.append("write_scope_missing_derived_from_phase")
    if write_scope not in WRITE_SCOPES:
        flags.append(f"invalid_write_scope:{write_scope}")
        write_scope = "WORK_ONLY"

    approval_state = str(data.get("approval_state") or "").strip().upper()
    if not approval_state:
        approval_state = _derive_approval_state(profile, phase, write_scope, task_id, repo)
        flags.append("approval_state_missing_derived")
    if approval_state not in APPROVAL_STATES:
        flags.append(f"invalid_approval_state:{approval_state}")
        approval_state = "PENDING"

    combo_ok, combo_reason = validate_state_combination(
        profile, phase, approval_state, write_scope, task_id, data, repo
    )
    if not combo_ok:
        flags.append(f"state_combo:{combo_reason}")

    allowed = [str(x) for x in (data.get("allowed_paths") or [])]
    if any(p.rstrip("/") == "specs/**" or p == "specs/*/**" for p in allowed):
        flags.append("wide_specs_glob_in_allowed_paths")

    verify = [str(x) for x in (data.get("verify_commands") or [])]
    return ActiveState(
        task_id=task_id,
        policy_profile=profile,
        phase=phase,
        approval_state=approval_state,
        write_scope=write_scope,
        allowed_paths=allowed,
        lane=lane or profile,
        status=status,
        verify_commands=verify,
        raw=data,
        anomaly_flags=flags,
    )


def analysis_phase(state: ActiveState) -> bool:
    return state.phase in {"BOOTSTRAP", "ANALYZING", "SPEC_READY"} or (
        state.write_scope == "WORK_ONLY"
        and state.phase not in {"IMPLEMENTING", "VERIFYING", "BLOCKED"}
    )


def implementation_phase(state: ActiveState) -> bool:
    return state.phase in {"IMPLEMENTING", "VERIFYING"} or state.write_scope == "IMPLEMENTATION"


def blocked_phase(state: ActiveState) -> bool:
    return state.phase == "BLOCKED"

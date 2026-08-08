"""Path write gating for structured Write/Edit/Delete tools."""

from __future__ import annotations

import fnmatch
import json
import os
from pathlib import Path

from .active_state import (
    PROFILE_RANK,
    ActiveState,
    analysis_phase,
    artifact_paths,
    implementation_approved,
    implementation_paths,
    implementation_phase,
    load_active,
    maintenance_paths,
    normalize_active,
    repo_root,
    specification_scope,
    validate_state_combination,
    wrapper_trusted,
)
from .spec_kit import (
    evaluate_specification_path,
    is_implementation_source_path,
    validate_active_spec_kit_mutation,
)

PROTECTED_PATHS = ("build/generated-spring-modulith/javadoc.json",)
SECRET_GLOBS = (
    ".env",
    ".env.*",
    "**/*.pem",
    "**/*.key",
    "**/credentials.json",
    "**/secrets.yaml",
)


def norm_path(path: str, repo: Path) -> str:
    p = path.replace("\\", "/")
    if p.startswith("./"):
        p = p[2:]
    abs_repo = str(repo.resolve()) + "/"
    resolved = str(repo.resolve())
    if p.startswith(abs_repo):
        p = p[len(abs_repo) :]
    elif p.startswith(resolved + "/"):
        p = p[len(resolved) + 1 :]
    return p.lstrip("/")


def path_matches(path: str, patterns: list[str]) -> bool:
    n = path
    for pat in patterns:
        if pat.endswith("/**"):
            prefix = pat[:-3].rstrip("/")
            if n == prefix or n.startswith(prefix + "/"):
                return True
        elif "*" in pat or "?" in pat:
            if fnmatch.fnmatch(n, pat):
                return True
        elif n == pat or n.startswith(pat.rstrip("/") + "/"):
            return True
    return False


def is_secret_path(path: str) -> bool:
    return path_matches(path, list(SECRET_GLOBS))


def is_protected_path(path: str) -> bool:
    return path in PROTECTED_PATHS


def validate_active_mutation(current: ActiveState, contents: str, repo: Path) -> tuple[bool, str]:
    try:
        new = json.loads(contents)
    except json.JSONDecodeError:
        return False, "ACTIVE.json contents are not valid JSON"
    new_state = normalize_active(new, repo)
    cur_rank = PROFILE_RANK.get(current.policy_profile, 0)
    new_rank = PROFILE_RANK.get(new_state.policy_profile, 0)
    if new_rank < cur_rank and os.environ.get("DEBINA_ALLOW_PROFILE_DOWNGRADE") != "1":
        return False, (
            f"profile downgrade blocked: {current.policy_profile} → {new_state.policy_profile}; "
            "use tools/agent/set-task-state --allow-downgrade with justification"
        )
    if new_state.task_id != current.task_id and current.task_id:
        return False, "cannot change task_id of active task in-place"
    if new_state.phase == "IMPLEMENTING" and new_state.policy_profile in {"STANDARD", "DECISION"}:
        if new_state.approval_state != "APPROVED" and not implementation_approved(
            new_state.policy_profile, new_state.task_id, repo
        ):
            return False, "cannot enter IMPLEMENTING without APPROVED"
    ok, reason = validate_active_spec_kit_mutation(current.raw, new)
    if not ok:
        return False, reason
    combo_ok, combo_reason = validate_state_combination(
        new_state.policy_profile,
        new_state.phase,
        new_state.approval_state,
        new_state.write_scope,
        new_state.task_id,
        new,
        repo,
    )
    if not combo_ok:
        return False, combo_reason
    return True, "ok"


def evaluate_write(
    path: str,
    *,
    tool: str,
    state: ActiveState | None = None,
    repo: Path | None = None,
    contents: str | None = None,
) -> tuple[str, str]:
    repo = repo or repo_root()
    n = norm_path(path, repo)

    if is_protected_path(n) or is_secret_path(n):
        return "deny", f"protected or secret path blocked: {n}"

    if state is None:
        if not n.startswith("work/"):
            return "deny", f"No work/ACTIVE.json — only work/** discovery writes allowed ({n})"
        return "allow", "bootstrap work-only"

    if not state.task_id:
        return "deny", "ACTIVE.json missing task_id"

    ok, reason = validate_state_combination(
        state.policy_profile,
        state.phase,
        state.approval_state,
        state.write_scope,
        state.task_id,
        state.raw,
        repo,
    )
    if not ok:
        return "deny", reason

    task_glob = state.task_work_glob
    task_prefix = f"work/active/{state.task_id}/"

    if n in {"work/ACTIVE.json", "work/QUEUE.md"}:
        if not wrapper_trusted():
            return "deny", "ACTIVE.json/QUEUE.md must be updated via tools/agent wrappers"
        if n == "work/ACTIVE.json" and contents:
            ok2, reason2 = validate_active_mutation(state, contents, repo)
            if not ok2:
                return "deny", reason2
        return "allow", "controlled wrapper state update"

    if n.startswith("work/active/") and not (
        n.startswith(task_prefix) or n == f"work/active/{state.task_id}"
    ):
        return "deny", f"path task id mismatch vs ACTIVE task_id={state.task_id}"

    if state.phase == "BLOCKED":
        maint = maintenance_paths(state.raw)
        if maint and path_matches(n, maint):
            return "allow", "BLOCKED maintenance path"
        if path_matches(n, [task_glob, f"work/active/{state.task_id}"]):
            return "allow", "BLOCKED task notes"
        return "deny", f"BLOCKED phase write denied: {n}"

    if specification_scope(state.write_scope):
        return evaluate_specification_path(n, state, repo, contents=contents)

    if state.write_scope == "WORK_ONLY":
        if n.startswith("specs/") or n == ".specify/feature.json":
            return "deny", "WORK_ONLY cannot write Spec Kit canonical paths"
        if is_implementation_source_path(n):
            return "deny", "WORK_ONLY cannot write implementation sources"
        if analysis_phase(state) and not implementation_phase(state):
            if path_matches(n, [task_glob, f"work/active/{state.task_id}"]):
                return "allow", "WORK_ONLY task artifact"
            if n.startswith("work/archive/") and wrapper_trusted():
                return "allow", "wrapper closeout archive"
            return "deny", f"WORK_ONLY cannot write outside task work dir: {n}"
        if path_matches(n, [task_glob, f"work/active/{state.task_id}"]):
            return "allow", "WORK_ONLY task artifact"

    if state.write_scope == "REVIEW":
        if path_matches(n, [task_glob]):
            return "allow", "review task notes"
        if n == "work/QUEUE.md" and wrapper_trusted():
            return "allow", "review queue wrapper"
        return "deny", f"REVIEW scope blocked: {n}"

    if state.write_scope == "CLOSEOUT":
        if wrapper_trusted() and n.startswith("work/"):
            return "allow", "closeout wrapper"
        if path_matches(n, [task_glob]):
            return "allow", "closeout task notes"
        return "deny", f"CLOSEOUT blocked without wrapper: {n}"

    if state.write_scope == "IMPLEMENTATION" or implementation_phase(state):
        if state.policy_profile in {"STANDARD", "DECISION"}:
            if state.approval_state == "REJECTED":
                return "deny", "approval_state=REJECTED"
            approved = state.approval_state == "APPROVED" or implementation_approved(
                state.policy_profile, state.task_id, repo
            )
            if not approved:
                return "deny", (
                    f"{state.policy_profile} implementation requires approval_state=APPROVED "
                    f"(compat: work/approvals/{state.task_id}.approved)"
                )
        allowed_patterns = (
            artifact_paths(state.raw) + implementation_paths(state.raw) + state.allowed_paths
        )
        overlay = [task_glob, f"work/active/{state.task_id}"]
        if path_matches(n, allowed_patterns + overlay):
            return "allow", "implementation path ok"
        return "deny", f"Path outside artifact/implementation paths: {n}"

    if not state.allowed_paths:
        return "deny", "ACTIVE.json missing allowed_paths for implementation"

    if state.policy_profile in {"STANDARD", "DECISION"}:
        if state.approval_state == "REJECTED":
            return "deny", "approval_state=REJECTED"
        approved = state.approval_state == "APPROVED" or implementation_approved(
            state.policy_profile, state.task_id, repo
        )
        if not approved:
            return "deny", (
                f"{state.policy_profile} implementation requires approval_state=APPROVED "
                f"(compat: work/approvals/{state.task_id}.approved)"
            )

    overlay = [task_glob, f"work/active/{state.task_id}"]
    if not path_matches(n, state.allowed_paths + overlay):
        return "deny", f"Path outside allowed_paths: {n}"

    _ = tool
    return "allow", "implementation path ok"


def evaluate_write_path(path: str, tool: str = "Write", contents: str | None = None) -> tuple[str, str]:
    repo = repo_root()
    return evaluate_write(path, tool=tool, state=load_active(repo), repo=repo, contents=contents)

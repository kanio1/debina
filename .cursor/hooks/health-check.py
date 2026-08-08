#!/usr/bin/env python3
"""Synthetic + anomaly health checks for Debina phased-approval hooks."""
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

GUARD = REPO / ".cursor" / "hooks" / "command-guard.py"
SCOPE = REPO / ".cursor" / "hooks" / "scope-approval-gate.py"
CHECK = REPO / ".cursor" / "hooks" / "checkpoint.py"
POST = REPO / ".cursor" / "hooks" / "shell-post-check.py"
PROBE = REPO / "tools" / "agent_policy" / "_bootstrap_probe.txt"
PROMOTE = REPO / "tools" / "agent" / "promote-spec-kit-feature"


def import_agent_policy():
    try:
        from agent_policy.active_state import (  # noqa: F401
            WRITE_SCOPES,
            load_active,
            normalize_active,
            validate_state_combination,
        )
        from agent_policy.shell_policy import classify_shell_command  # noqa: F401
        from agent_policy.write_gate import evaluate_write  # noqa: F401
    except Exception as exc:
        print(f"AGENT_POLICY_IMPORT_FAILED: {exc.__class__.__name__}: {exc}")
        raise SystemExit(1) from exc
    return load_active, normalize_active, validate_state_combination, classify_shell_command, evaluate_write, WRITE_SCOPES


def run_hook(script: Path, payload: dict) -> dict:
    proc = subprocess.run(
        [sys.executable, str(script)],
        input=json.dumps(payload),
        text=True,
        capture_output=True,
        cwd=str(REPO),
        check=False,
    )
    stdout = proc.stdout.strip().splitlines()
    if not stdout:
        return {"_raw": proc.stdout, "_stderr": proc.stderr, "_code": proc.returncode}
    try:
        return json.loads(stdout[-1])
    except json.JSONDecodeError:
        return {"_raw": proc.stdout, "_stderr": proc.stderr, "_code": proc.returncode}


def expect(name: str, cond: bool, detail: str = "") -> None:
    if cond:
        print(f"PASS {name}")
    else:
        print(f"FAIL {name}: {detail}")
        raise SystemExit(1)


def main() -> int:
    load_active, normalize_active, validate_state_combination, classify_shell_command, evaluate_write, WRITE_SCOPES = (
        import_agent_policy()
    )

    r = run_hook(GUARD, {"command": "git status --short"})
    expect("safe-command-allow", r.get("permission") == "allow", str(r))
    r = run_hook(GUARD, {"command": "git push origin HEAD"})
    expect("git-push-deny", r.get("permission") == "deny", str(r))
    r = run_hook(GUARD, {"command": "rm -rf /tmp/anything"})
    expect("rm-deny", r.get("permission") == "deny", str(r))

    expect("specification-scope-exists", "SPECIFICATION" in WRITE_SCOPES, str(WRITE_SCOPES))
    expect("promote-wrapper-exists", PROMOTE.is_file(), "missing promote-spec-kit-feature")
    expect("bootstrap-probe-absent", not PROBE.is_file(), f"remove stale {PROBE}")

    state = load_active(REPO)
    if state is not None:
        expect(
            "no-technical-fast-bypass",
            "technical_fast_bypass" not in state.anomaly_flags,
            str(state.anomaly_flags),
        )
        if state.phase in {"BOOTSTRAP", "ANALYZING", "SPEC_READY"}:
            expect(
                "analyzing-pending-valid",
                state.approval_state in {"NOT_REQUIRED", "PENDING"}
                or state.policy_profile == "FAST",
                f"{state.approval_state}",
            )
        expect("allowed-paths-present", bool(state.allowed_paths), "missing allowed_paths")
        expect(
            "schema-fields-present",
            bool(state.policy_profile and state.phase and state.write_scope and state.approval_state),
            "missing phased fields",
        )
        if any(p.rstrip("/") == "specs/**" for p in state.allowed_paths):
            expect("no-wide-specs-glob", False, "allowed_paths contains specs/**")

    perm, reason = classify_shell_command(
        "python3 -c \"open('README.md','w').write('x')\"",
        state=state,
        repo=REPO,
    )
    expect("shell-write-bypass-deny", perm == "deny", f"{perm} {reason}")

    perm, reason = classify_shell_command(
        "python3 tools/agent/promote_spec_kit_feature.py --dry-run",
        state=state,
        repo=REPO,
    )
    expect("promote-direct-python-deny", perm == "deny", f"{perm} {reason}")

    for cmd in ("./mvnw test", "pnpm test", "pnpm build"):
        perm, reason = classify_shell_command(cmd, state=state, repo=REPO)
        expect(f"allow-{cmd}", perm == "allow", f"{perm} {reason}")

    expect(
        "bootstrap-wrapper-exists",
        (REPO / "tools/agent/bootstrap-task").is_file(),
        "missing bootstrap-task",
    )

    perm, reason = classify_shell_command("sed -i 's/a/b/' README.md", state=state, repo=REPO)
    expect("fail-closed-sed", perm == "deny", f"{perm} {reason}")

    post = POST.read_text(encoding="utf-8")
    expect("post-check-exists", POST.is_file(), "missing shell-post-check")
    expect("post-check-no-restore", "git restore" not in post, "auto restore present")

    sample = normalize_active(
        {
            "task_id": "HEALTH-STD-AN",
            "policy_profile": "STANDARD",
            "lane": "STANDARD",
            "phase": "ANALYZING",
            "approval_state": "PENDING",
            "write_scope": "WORK_ONLY",
            "allowed_paths": ["work/active/HEALTH-STD-AN/**"],
            "verify_commands": ["./tools/agent/verify-fast"],
        },
        REPO,
    )
    expect("std-analyzing-pending-sample", sample.approval_state == "PENDING", str(sample))
    perm, reason = evaluate_write(
        "work/active/HEALTH-STD-AN/plan.md",
        tool="Write",
        state=sample,
        repo=REPO,
    )
    expect("std-work-only-write", perm == "allow", f"{perm} {reason}")

    ok, _ = validate_state_combination(
        "STANDARD", "ANALYZING", "PENDING", "SPECIFICATION", "HEALTH-STD-AN",
        {
            "task_id": "HEALTH-STD-AN",
            "spec_kit": {"feature_slug": "markdown-h1-checker"},
        },
        REPO,
    )
    expect("specification-combo-with-mirror", ok, "mirror binding should satisfy SPECIFICATION")

    r = run_hook(CHECK, {"hook_event_name": "stop"})
    expect("checkpoint-no-followup", "followup_message" not in r, str(r))

    syn = subprocess.run(
        [sys.executable, str(REPO / "tools/agent/run_hook_synthetic_tests.py")],
        cwd=str(REPO),
        capture_output=True,
        text=True,
        check=False,
    )
    print(syn.stdout)
    if syn.returncode != 0:
        print(syn.stderr)
        expect("synthetic-suite", False, f"exit {syn.returncode}")
    else:
        expect("synthetic-suite", True)

    print("HEALTH_CHECK: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

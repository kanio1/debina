#!/usr/bin/env python3
"""Synthetic regression tests for phased approval + shell write policy."""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from agent_policy.active_state import WRAPPER_ENV, normalize_active  # noqa: E402
from agent_policy.shell_policy import (  # noqa: E402
    classify_shell_command,
    unauthorized_new_paths,
)
from agent_policy.write_gate import evaluate_write  # noqa: E402

PASS = 0
FAILS: list[str] = []


def expect(name: str, cond: bool, detail: str = "") -> None:
    global PASS
    if cond:
        print(f"PASS {name}")
        PASS += 1
    else:
        print(f"FAIL {name}: {detail}")
        FAILS.append(name)


def run_hook(script: Path, payload: dict) -> dict:
    proc = subprocess.run(
        [sys.executable, str(script)],
        input=json.dumps(payload),
        text=True,
        capture_output=True,
        cwd=str(REPO),
        check=False,
        env={**os.environ, "DEBINA_AGENT_WRAPPER": os.environ.get(WRAPPER_ENV, "")},
    )
    lines = [l for l in proc.stdout.strip().splitlines() if l.strip()]
    if not lines:
        return {"_raw": proc.stdout, "_stderr": proc.stderr, "_code": proc.returncode}
    try:
        return json.loads(lines[-1])
    except json.JSONDecodeError:
        return {"_raw": proc.stdout, "_stderr": proc.stderr, "_code": proc.returncode}


def with_active(data: dict):
    active = REPO / "work" / "ACTIVE.json"
    previous = active.read_text(encoding="utf-8") if active.is_file() else None
    active.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    return previous


def restore_active(previous: str | None) -> None:
    active = REPO / "work" / "ACTIVE.json"
    if previous is None:
        if active.is_file():
            active.unlink()
    else:
        active.write_text(previous, encoding="utf-8")


def std_analyzing(task: str = "SYN-STD-AN") -> dict:
    return {
        "task_id": task,
        "title": "synthetic",
        "lane": "STANDARD",
        "policy_profile": "STANDARD",
        "phase": "ANALYZING",
        "approval_state": "NOT_REQUIRED",
        "write_scope": "WORK_ONLY",
        "status": "ACTIVE",
        "allowed_paths": [f"work/active/{task}/**"],
        "approval_required": False,
        "verify_commands": ["./tools/agent/verify-fast"],
    }


def std_impl(task: str = "SYN-STD-IM", approved: bool = False) -> dict:
    return {
        "task_id": task,
        "title": "synthetic",
        "lane": "STANDARD",
        "policy_profile": "STANDARD",
        "phase": "IMPLEMENTING",
        "approval_state": "APPROVED" if approved else "PENDING",
        "write_scope": "IMPLEMENTATION",
        "status": "IMPLEMENTING",
        "allowed_paths": ["docs/governance/CURSOR-LEAN-HARNESS-MANUAL-SETUP.md"],
        "approval_required": True,
        "verify_commands": ["./tools/agent/verify-fast"],
    }


def main() -> int:
    scope = REPO / ".cursor" / "hooks" / "scope-approval-gate.py"
    guard = REPO / ".cursor" / "hooks" / "command-guard.py"
    active = REPO / "work" / "ACTIVE.json"
    previous = active.read_text(encoding="utf-8") if active.is_file() else None

    created_dirs: list[Path] = []
    created_files: list[Path] = []

    try:
        # 1 bootstrap with no active — function-level via evaluate_write
        if active.is_file():
            active.unlink()
        perm, reason = evaluate_write(
            "work/active/X/plan.md", tool="Write", state=None, repo=REPO
        )
        expect("01-no-active-work-allow", perm == "allow", f"{perm} {reason}")

        # 2 second bootstrap deny when active exists — tested via bootstrap script later
        # prepare analyzing task
        task = "SYN-STD-AN"
        tdir = REPO / "work" / "active" / task
        tdir.mkdir(parents=True, exist_ok=True)
        created_dirs.append(tdir)
        (tdir / "plan.md").write_text("# plan\n", encoding="utf-8")
        (tdir / "progress.md").write_text("# progress\n", encoding="utf-8")
        created_files.extend([tdir / "plan.md", tdir / "progress.md"])
        prev = with_active(std_analyzing(task))

        r = run_hook(
            scope,
            {"tool_name": "Write", "tool_input": {"path": f"work/active/{task}/plan.md", "contents": "x"}},
        )
        expect("03-std-analyzing-plan-allow", r.get("permission") == "allow", str(r))

        r = run_hook(
            scope,
            {
                "tool_name": "Write",
                "tool_input": {"path": f"work/active/{task}/progress.md", "contents": "x"},
            },
        )
        expect("04-std-analyzing-progress-allow", r.get("permission") == "allow", str(r))

        r = run_hook(
            scope,
            {"tool_name": "Write", "tool_input": {"path": "work/QUEUE.md", "contents": "NOW\n"}},
        )
        expect("05-std-analyzing-queue-direct-deny", r.get("permission") == "deny", str(r))

        # controlled queue via wrapper env
        os.environ[WRAPPER_ENV] = "1"
        r = run_hook(
            scope,
            {"tool_name": "Write", "tool_input": {"path": "work/QUEUE.md", "contents": "NOW\n"}},
        )
        expect("05b-std-analyzing-queue-wrapper-allow", r.get("permission") == "allow", str(r))
        os.environ.pop(WRAPPER_ENV, None)

        r = run_hook(
            scope,
            {"tool_name": "Write", "tool_input": {"path": "backend/pom.xml", "contents": "x"}},
        )
        expect("06-std-analyzing-backend-deny", r.get("permission") == "deny", str(r))

        r = run_hook(
            scope,
            {
                "tool_name": "Write",
                "tool_input": {"path": ".cursor/hooks/command-guard.py", "contents": "x"},
            },
        )
        expect("07-std-analyzing-hooks-deny", r.get("permission") == "deny", str(r))

        # 8 implementing without approval
        with_active(std_impl(approved=False))
        im = REPO / "work" / "active" / "SYN-STD-IM"
        im.mkdir(parents=True, exist_ok=True)
        created_dirs.append(im)
        (im / "plan.md").write_text("# plan\n", encoding="utf-8")
        created_files.append(im / "plan.md")
        r = run_hook(
            scope,
            {
                "tool_name": "Write",
                "tool_input": {
                    "path": "docs/governance/CURSOR-LEAN-HARNESS-MANUAL-SETUP.md",
                    "contents": "x",
                },
            },
        )
        expect("08-std-impl-no-approval-deny", r.get("permission") == "deny", str(r))

        # 9 with approval marker
        appr = REPO / "work" / "approvals" / "SYN-STD-IM.approved"
        appr.write_text("synthetic\n", encoding="utf-8")
        created_files.append(appr)
        with_active(std_impl(approved=True))
        r = run_hook(
            scope,
            {
                "tool_name": "Write",
                "tool_input": {
                    "path": "docs/governance/CURSOR-LEAN-HARNESS-MANUAL-SETUP.md",
                    "contents": "x",
                },
            },
        )
        expect("09-std-impl-approved-allow", r.get("permission") == "allow", str(r))

        r = run_hook(
            scope,
            {"tool_name": "Write", "tool_input": {"path": "backend/pom.xml", "contents": "x"}},
        )
        expect("10-std-impl-outside-paths-deny", r.get("permission") == "deny", str(r))

        # 11 FAST outside paths
        with_active(
            {
                "task_id": "SYN-FAST",
                "title": "f",
                "lane": "FAST",
                "policy_profile": "FAST",
                "phase": "IMPLEMENTING",
                "approval_state": "NOT_REQUIRED",
                "write_scope": "IMPLEMENTATION",
                "status": "IMPLEMENTING",
                "allowed_paths": ["docs/governance/CURSOR-LEAN-HARNESS-MANUAL-SETUP.md"],
                "verify_commands": ["./tools/agent/verify-fast"],
            }
        )
        fd = REPO / "work" / "active" / "SYN-FAST"
        fd.mkdir(parents=True, exist_ok=True)
        created_dirs.append(fd)
        (fd / "plan.md").write_text("# p\n", encoding="utf-8")
        created_files.append(fd / "plan.md")
        r = run_hook(
            scope,
            {"tool_name": "Write", "tool_input": {"path": "backend/pom.xml", "contents": "x"}},
        )
        expect("11-fast-outside-paths-deny", r.get("permission") == "deny", str(r))

        # 12 profile downgrade without decision
        cur = std_impl(approved=True)
        with_active(cur)
        state = normalize_active(cur, REPO)
        os.environ.pop("DEBINA_ALLOW_PROFILE_DOWNGRADE", None)
        os.environ.pop(WRAPPER_ENV, None)
        lowered = dict(cur)
        lowered["policy_profile"] = "FAST"
        lowered["lane"] = "FAST"
        from agent_policy.write_gate import validate_active_mutation

        ok, reason = validate_active_mutation(state, json.dumps(lowered), REPO)
        expect("12-downgrade-deny", not ok, reason)

        # 13 escalate FAST -> STANDARD with reason path (allowed via set-task-state conceptually)
        ok2, _ = validate_active_mutation(
            normalize_active(
                {
                    **cur,
                    "policy_profile": "FAST",
                    "lane": "FAST",
                    "approval_state": "NOT_REQUIRED",
                },
                REPO,
            ),
            json.dumps(cur),
            REPO,
        )
        expect("13-escalate-allow", ok2, "escalate should be allowed")

        # Shell denials 14-18
        st = normalize_active(std_impl(approved=True), REPO)
        for name, cmd in [
            ("14-echo-redirect-deny", "echo x > README.md"),
            ("15-heredoc-deny", "cat <<EOF > README.md\nhello\nEOF"),
            ("16-tee-deny", "echo x | tee README.md"),
            ("17-sed-i-deny", "sed -i 's/a/b/' README.md"),
            ("18-python-write-deny", "python3 -c \"open('README.md','w').write('x')\""),
        ]:
            perm, reason = classify_shell_command(cmd, state=st, repo=REPO)
            expect(name, perm == "deny", f"{perm} {reason}")

        # 19 shell after write denial — same as interpreter write deny
        perm, reason = classify_shell_command(
            "python3 -c \"from pathlib import Path; Path('backend/pom.xml').write_text('x')\"",
            state=st,
            repo=REPO,
        )
        expect("19-shell-bypass-deny", perm == "deny", f"{perm} {reason}")

        # 20 read-only allow
        for name, cmd in [
            ("20a-rg", "rg ACTIVE work"),
            ("20b-find", "find work -maxdepth 2 -type f"),
            ("20c-cat", "cat work/QUEUE.md"),
            ("20d-git-diff", "git diff --stat"),
            ("20e-git-status", "git status --short"),
        ]:
            perm, reason = classify_shell_command(cmd, state=st, repo=REPO)
            expect(name, perm == "allow", f"{perm} {reason}")

        perm, reason = classify_shell_command("./mvnw test", state=st, repo=REPO)
        expect("21-mvnw-test-allow", perm == "allow", f"{perm} {reason}")
        perm, reason = classify_shell_command("pnpm test", state=st, repo=REPO)
        expect("22-pnpm-test-allow", perm == "allow", f"{perm} {reason}")
        perm, reason = classify_shell_command("pnpm build", state=st, repo=REPO)
        expect("23-build-allow", perm == "allow", f"{perm} {reason}")

        # 24 bootstrap wrapper allow classification
        perm, reason = classify_shell_command(
            "bash tools/agent/bootstrap-task --task-id X --title t --policy-profile FAST",
            state=None,
            repo=REPO,
        )
        expect("24-bootstrap-wrapper-allow", perm == "allow", f"{perm} {reason}")

        # 25 mismatched task path
        with_active(std_analyzing("SYN-STD-AN"))
        r = run_hook(
            scope,
            {
                "tool_name": "Write",
                "tool_input": {"path": "work/active/OTHER/plan.md", "contents": "x"},
            },
        )
        expect("25-task-id-mismatch-deny", r.get("permission") == "deny", str(r))

        # 26 partial bootstrap leaves no ACTIVE — run failing bootstrap
        restore_active(None)
        # invalid id should fail before ACTIVE write
        proc = subprocess.run(
            [
                sys.executable,
                str(REPO / "tools/agent/bootstrap_task.py"),
                "--task-id",
                "bad id",
                "--title",
                "t",
                "--policy-profile",
                "FAST",
            ],
            cwd=str(REPO),
            capture_output=True,
            text=True,
            env={**os.environ, WRAPPER_ENV: "1"},
            check=False,
        )
        expect("26-failed-bootstrap-no-active", not (REPO / "work" / "ACTIVE.json").is_file(), proc.stderr)

        # 27 pre-existing paths not attributed
        before = {"HANDOFF.md", "work/QUEUE.md"}
        after = {"HANDOFF.md", "work/QUEUE.md"}
        bad = unauthorized_new_paths(before, after, state=None, repo=REPO)
        expect("27-preexisting-not-flagged", bad == [], str(bad))

        # 28 unauthorized new path detected
        bad = unauthorized_new_paths(before, before | {"README.md"}, state=None, repo=REPO)
        expect("28-new-unauthorized-detected", "README.md" in bad, str(bad))

        # 29 post-check does not restore — ensure function has no git restore call
        src = (REPO / ".cursor/hooks/shell-post-check.py").read_text(encoding="utf-8")
        expect(
            "29-no-auto-restore",
            "subprocess" not in src or ("git\", \"restore\"" not in src and "git restore" not in src),
            "found restore invocation",
        )
        expect(
            "29b-no-restore-call",
            "run([\"git\", \"restore\"" not in src and "git checkout" not in src,
            "found checkout/restore call",
        )

        # 30 STANDARD analysis no technical FAST
        st30 = normalize_active(std_analyzing(), REPO)
        expect("30-no-technical-fast", "technical_fast_bypass" not in st30.anomaly_flags, str(st30.anomaly_flags))
        expect(
            "30b-std-work-only-no-approval",
            st30.approval_state == "NOT_REQUIRED" and st30.policy_profile == "STANDARD",
            str(st30),
        )

        # 2 second bootstrap deny
        with_active(std_analyzing())
        proc = subprocess.run(
            [
                sys.executable,
                str(REPO / "tools/agent/bootstrap_task.py"),
                "--task-id",
                "ANOTHER",
                "--title",
                "t",
                "--policy-profile",
                "FAST",
            ],
            cwd=str(REPO),
            capture_output=True,
            text=True,
            env={**os.environ, WRAPPER_ENV: "1"},
            check=False,
        )
        expect("02-second-bootstrap-deny", proc.returncode != 0, proc.stderr)

        # --- Spec Kit specification scope tests ---
        import agent_policy  # noqa: F401
        expect("sk-import-agent-policy", True)

        compile_proc = subprocess.run(
            [sys.executable, "-m", "compileall", "-q", "tools/agent_policy"],
            cwd=str(REPO),
            capture_output=True,
            text=True,
            check=False,
        )
        expect("sk-compileall-pass", compile_proc.returncode == 0, compile_proc.stderr)

        pilot = "PILOT-DEBINA-SPEC-KIT-MICRO-FEATURE"
        feature_dir = "specs/001-markdown-h1-checker"
        spec_state = {
            "task_id": pilot,
            "policy_profile": "STANDARD",
            "lane": "STANDARD",
            "phase": "ANALYZING",
            "approval_state": "PENDING",
            "write_scope": "SPECIFICATION",
            "status": "ACTIVE",
            "allowed_paths": [f"work/active/{pilot}/**", ".specify/feature.json", f"{feature_dir}/**"],
            "artifact_paths": [
                f"work/active/{pilot}/**",
                ".specify/feature.json",
                f"{feature_dir}/**",
            ],
            "implementation_paths": [
                "tools/spec-kit-pilot/**",
                "tests/tools/spec-kit-pilot/**",
            ],
            "spec_kit": {
                "feature_id": "001-markdown-h1-checker",
                "feature_slug": "markdown-h1-checker",
                "feature_directory": feature_dir,
                "feature_state_path": ".specify/feature.json",
            },
            "verify_commands": ["./tools/agent/verify-fast"],
        }
        with_active(spec_state)
        st_spec = normalize_active(spec_state, REPO)

        perm, reason = evaluate_write(
            f"work/active/{pilot}/plan.md", tool="Write", state=st_spec, repo=REPO
        )
        expect("sk-std-analyzing-pending-spec-scope", perm == "allow", f"{perm} {reason}")

        for name, path in [
            ("sk-active-spec-md", f"{feature_dir}/spec.md"),
            ("sk-active-plan-md", f"{feature_dir}/plan.md"),
            ("sk-active-tasks-md", f"{feature_dir}/tasks.md"),
            ("sk-active-checklist", f"{feature_dir}/checklists/requirements.md"),
        ]:
            perm, reason = evaluate_write(path, tool="Write", state=st_spec, repo=REPO)
            expect(name, perm == "allow", f"{perm} {reason}")

        fj = json.dumps(
            {
                "feature_directory": feature_dir,
                "debina_task_id": pilot,
                "short_name": "markdown-h1-checker",
                "feature_number": "001",
                "feature_id": "001-markdown-h1-checker",
            }
        )
        perm, reason = evaluate_write(
            ".specify/feature.json", tool="Write", state=st_spec, repo=REPO, contents=fj
        )
        expect("sk-feature-json-valid", perm == "allow", f"{perm} {reason}")

        perm, reason = evaluate_write(
            "specs/002-other-feature/spec.md", tool="Write", state=st_spec, repo=REPO
        )
        expect("sk-foreign-feature-deny", perm == "deny", f"{perm} {reason}")

        perm, reason = evaluate_write(
            "specs/001-other-slug/spec.md", tool="Write", state=st_spec, repo=REPO
        )
        expect("sk-other-slug-deny", perm == "deny", f"{perm} {reason}")

        perm, reason = evaluate_write(
            "specs/002-markdown-h1-checker/spec.md", tool="Write", state=st_spec, repo=REPO
        )
        expect("sk-other-number-deny", perm == "deny", f"{perm} {reason}")

        from agent_policy.spec_kit import normalize_repo_path

        expect("sk-traversal-posix", normalize_repo_path("../specs/001-x") is None, "posix traversal")
        expect("sk-traversal-win", normalize_repo_path("specs\\..\\secret") is None, "win traversal")
        expect("sk-abs-posix", normalize_repo_path("/specs/001-x") is None, "abs posix")
        expect("sk-abs-win", normalize_repo_path("C:/specs/001-x") is None, "abs win")

        perm, reason = evaluate_write("backend/pom.xml", tool="Write", state=st_spec, repo=REPO)
        expect("sk-source-analyzing-deny", perm == "deny", f"{perm} {reason}")

        perm, reason = evaluate_write(
            "tests/tools/spec-kit-pilot/test_x.py", tool="Write", state=st_spec, repo=REPO
        )
        expect("sk-test-code-analyzing-deny", perm == "deny", f"{perm} {reason}")

        perm, reason = evaluate_write(
            ".specify/memory/constitution.md", tool="Write", state=st_spec, repo=REPO
        )
        expect("sk-constitution-deny", perm == "deny", f"{perm} {reason}")

        perm, reason = evaluate_write(
            ".specify/templates/spec-template.md", tool="Write", state=st_spec, repo=REPO
        )
        expect("sk-template-deny", perm == "deny", f"{perm} {reason}")

        perm, reason = evaluate_write(
            ".specify/integration.json", tool="Write", state=st_spec, repo=REPO
        )
        expect("sk-integration-deny", perm == "deny", f"{perm} {reason}")

        work_only = dict(spec_state)
        work_only["write_scope"] = "WORK_ONLY"
        st_wo = normalize_active(work_only, REPO)
        perm, reason = evaluate_write(f"{feature_dir}/spec.md", tool="Write", state=st_wo, repo=REPO)
        expect("sk-work-only-spec-deny", perm == "deny", f"{perm} {reason}")

        spec_ready = dict(spec_state)
        spec_ready["phase"] = "SPEC_READY"
        spec_ready["write_scope"] = "SPECIFICATION"
        st_sr = normalize_active(spec_ready, REPO)
        perm, reason = evaluate_write(f"{feature_dir}/spec.md", tool="Write", state=st_sr, repo=REPO)
        expect("sk-spec-ready-specification-deny", perm == "deny", f"{perm} {reason}")

        impl_spec = dict(spec_state)
        impl_spec["phase"] = "IMPLEMENTING"
        impl_spec["write_scope"] = "SPECIFICATION"
        impl_spec["approval_state"] = "APPROVED"
        st_is = normalize_active(impl_spec, REPO)
        perm, reason = evaluate_write(f"{feature_dir}/spec.md", tool="Write", state=st_is, repo=REPO)
        expect("sk-implementing-specification-deny", perm == "deny", f"{perm} {reason}")

        impl_pending = dict(spec_state)
        impl_pending["phase"] = "IMPLEMENTING"
        impl_pending["write_scope"] = "IMPLEMENTATION"
        impl_pending["approval_state"] = "PENDING"
        st_ip = normalize_active(impl_pending, REPO)
        perm, reason = evaluate_write(
            "tools/spec-kit-pilot/markdown_h1_check.py", tool="Write", state=st_ip, repo=REPO
        )
        expect("sk-implementing-pending-deny", perm == "deny", f"{perm} {reason}")

        impl_ok = dict(impl_pending)
        impl_ok["approval_state"] = "APPROVED"
        st_io = normalize_active(impl_ok, REPO)
        perm, reason = evaluate_write(
            "tools/spec-kit-pilot/markdown_h1_check.py", tool="Write", state=st_io, repo=REPO
        )
        expect("sk-implementing-approved-impl-path", perm == "allow", f"{perm} {reason}")

        perm, reason = evaluate_write(f"{feature_dir}/tasks.md", tool="Write", state=st_io, repo=REPO)
        expect("sk-implementing-approved-tasks", perm == "allow", f"{perm} {reason}")

        perm, reason = evaluate_write(
            "specs/002-other/spec.md", tool="Write", state=st_io, repo=REPO
        )
        expect("sk-implementing-foreign-deny", perm == "deny", f"{perm} {reason}")

        pending_an = dict(spec_state)
        pending_an["write_scope"] = "WORK_ONLY"
        st_pa = normalize_active(pending_an, REPO)
        expect("sk-analyzing-pending-health-ok", st_pa.approval_state == "PENDING", str(st_pa))

        fast_state = {
            "task_id": "SYN-FAST-NR",
            "policy_profile": "FAST",
            "lane": "FAST",
            "phase": "ANALYZING",
            "approval_state": "NOT_REQUIRED",
            "write_scope": "WORK_ONLY",
            "status": "ACTIVE",
            "allowed_paths": ["work/active/SYN-FAST-NR/**"],
            "verify_commands": ["./tools/agent/verify-fast"],
        }
        st_fast = normalize_active(fast_state, REPO)
        expect("sk-fast-not-required", st_fast.approval_state == "NOT_REQUIRED", str(st_fast))

        perm, reason = classify_shell_command(
            f"echo x > {feature_dir}/spec.md", state=st_spec, repo=REPO
        )
        expect("sk-shell-redirect-spec-deny", perm == "deny", f"{perm} {reason}")

        perm, reason = classify_shell_command(
            f"python3 -c \"open('{feature_dir}/spec.md','w').write('x')\"",
            state=st_spec,
            repo=REPO,
        )
        expect("sk-shell-python-spec-deny", perm == "deny", f"{perm} {reason}")

        r = run_hook(
            scope,
            {
                "tool_name": "Write",
                "tool_input": {"path": f"{feature_dir}/spec.md", "contents": "x"},
            },
        )
        expect("sk-hook-write-spec-allow", r.get("permission") == "allow", str(r))

        perm, reason = classify_shell_command(
            "python3 tools/agent/promote_spec_kit_feature.py",
            state=st_spec,
            repo=REPO,
        )
        expect("sk-promote-direct-python-deny", perm == "deny", f"{perm} {reason}")

        perm, reason = classify_shell_command(
            "./tools/agent/promote-spec-kit-feature --dry-run",
            state=st_spec,
            repo=REPO,
        )
        expect("sk-promote-wrapper-allow", perm == "allow", f"{perm} {reason}")

        proc = subprocess.run(
            [sys.executable, str(REPO / "tools/agent/promote_spec_kit_feature.py"), "--dry-run"],
            cwd=str(REPO),
            capture_output=True,
            text=True,
            env={**os.environ, WRAPPER_ENV: "1"},
            check=False,
        )
        expect("sk-promotion-dry-run-pass", proc.returncode == 0, proc.stderr + proc.stdout)

        probe = REPO / "tools/agent_policy/_bootstrap_probe.txt"
        expect("sk-bootstrap-probe-absent", not probe.is_file(), str(probe))

    finally:
        for f in created_files:
            if f.is_file():
                f.unlink()
        for d in created_dirs:
            if d.is_dir():
                try:
                    d.rmdir()
                except OSError:
                    for child in d.iterdir():
                        if child.is_file():
                            child.unlink()
                    try:
                        d.rmdir()
                    except OSError:
                        pass
        restore_active(previous)

    print(f"SYNTHETIC: {PASS} passed, {len(FAILS)} failed")
    if FAILS:
        print("FAILED:", ", ".join(FAILS))
        return 1
    print("SYNTHETIC: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

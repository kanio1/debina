"""Shell command write-effect classification and post-command diff checks."""

from __future__ import annotations

import re
import subprocess
from pathlib import Path

from .active_state import ActiveState, load_active, repo_root
from .write_gate import evaluate_write, norm_path

BUILD_OUTPUT_PREFIXES = (
    "backend/target/",
    "frontend/.next/",
    "frontend/dist/",
    "frontend/node_modules/",
    "node_modules/",
    "build/",
    "dagger/.dagger/",
    ".pytest_cache/",
    "coverage/",
)

READ_ONLY_PREFIXES = (
    "pwd",
    "ls",
    "eza",
    "tree",
    "cat ",
    "bat ",
    "head ",
    "tail ",
    "less ",
    "more ",
    "grep ",
    "rg ",
    "fd ",
    "find ",
    "sort",
    "uniq",
    "wc ",
    "stat ",
    "file ",
    "sha256sum",
    "md5sum",
    "git status",
    "git diff",
    "git log",
    "git show",
    "git rev-parse",
    "git ls-files",
    "git grep",
    "git blame",
    "git branch",
    "test ",
    "command -v",
    "which ",
    "python3 -m py_compile",
    "python3 -m json.tool",
)

TEST_BUILD_PREFIXES = (
    "./mvnw",
    "mvnw",
    "./tools/agent/",
    "tools/agent/",
    "python3 tools/",
    "bash tools/",
    "python3 .cursor/hooks/health-check.py",
    "pnpm test",
    "pnpm run test",
    "pnpm lint",
    "pnpm run lint",
    "pnpm typecheck",
    "pnpm run typecheck",
    "pnpm build",
    "pnpm run build",
    "pnpm exec",
    "npm test",
    "npm run test",
    "npm run build",
    "pytest",
    "dagger ",
)

DANGEROUS_GIT = re.compile(
    r"\bgit\s+(push|commit|merge|rebase|reset|clean|restore|checkout|switch)\b",
    re.I,
)
EXPLICIT_WRITE_CMDS = re.compile(
    r"(^|[;&|]\s*)(tee|truncate|touch|cp|mv|install|dd|rsync)\b|"
    r"\bsed\s+-i\b|"
    r"\bperl\s+-i\b|"
    r"\bjq\s+[^\n]*\s+-i\b|"
    r"\byq\s+[^\n]*\s+-i\b",
    re.I,
)
REDIRECT_RE = re.compile(r"(>>?)\s*([^\s|;]+)")
HEREDOC_RE = re.compile(r"<<\s*['\"]?(\w+)['\"]?")


def is_generated_output(rel: str) -> bool:
    return any(rel == p.rstrip("/") or rel.startswith(p) for p in BUILD_OUTPUT_PREFIXES)


def is_gitignored(repo: Path, rel: str) -> bool:
    proc = subprocess.run(
        ["git", "check-ignore", "-q", rel],
        cwd=str(repo),
        capture_output=True,
        text=True,
        check=False,
    )
    return proc.returncode == 0


def git_porcelain(repo: Path) -> set[str]:
    try:
        proc = subprocess.run(
            ["git", "status", "--porcelain"],
            cwd=str(repo),
            capture_output=True,
            text=True,
            check=False,
        )
    except OSError:
        return set()
    paths: set[str] = set()
    for line in proc.stdout.splitlines():
        if len(line) < 4:
            continue
        entry = line[3:].strip()
        if " -> " in entry:
            entry = entry.split(" -> ", 1)[1]
        paths.add(entry)
    return paths


def _extract_redirect_targets(command: str) -> list[str]:
    targets: list[str] = []
    for m in REDIRECT_RE.finditer(command):
        tgt = m.group(2).strip().strip("'\"")
        if tgt in {"/dev/null", "&1", "&2"}:
            continue
        targets.append(tgt)
    return targets


def _looks_like_interpreter_write(command: str) -> bool:
    low = command.lower()
    if not any(x in low for x in ("python", "node ", "ruby ")):
        return False
    if "py_compile" in low or "json.tool" in low:
        return False
    if "tools/agent" in low or ".cursor/hooks/health-check" in low or "tools/agent-policy" in low:
        return False
    markers = (
        "open(",
        "write_text",
        "write_bytes",
        "path.write",
        "fs.write",
        "writefile",
        "shutil.",
        "unlink(",
        "makedirs",
    )
    return any(m in low for m in markers)


def _targets_from_tee(command: str) -> list[str]:
    parts = command.split()
    out: list[str] = []
    for i, p in enumerate(parts):
        if p == "tee" or p.endswith("/tee"):
            for cand in parts[i + 1 :]:
                if cand.startswith("-"):
                    continue
                out.append(cand.strip("'\""))
            break
    return out


def _is_shell_blocked_spec_target(rel: str) -> bool:
    return (
        rel == ".specify/feature.json"
        or rel.startswith("specs/")
        or rel.startswith(".specify/feature.json")
    )


def _shell_target_allowed(rel: str, state: ActiveState | None, repo: Path) -> tuple[str, str]:
    if not rel or rel in {"/dev/null"}:
        return "allow", "devnull"
    if is_generated_output(rel) or is_gitignored(repo, rel):
        return "allow", "generated/ignored"
    if state is None:
        if rel in {"work/ACTIVE.json", "work/QUEUE.md"}:
            return "deny", "Shell must not write ACTIVE/QUEUE; use tools/agent wrappers"
        if rel.startswith("work/"):
            return "allow", "work bootstrap"
        return "deny", f"Shell write to non-work path without ACTIVE: {rel}"
    return evaluate_write(rel, tool="Shell", state=state, repo=repo)


def classify_shell_command(
    command: str, *, state: ActiveState | None = None, repo: Path | None = None
) -> tuple[str, str]:
    repo = repo or repo_root()
    if state is None:
        state = load_active(repo)
    stripped = command.strip()
    if not stripped:
        return "allow", "empty"

    if DANGEROUS_GIT.search(stripped):
        return "deny", "destructive or write git command blocked"
    if re.search(r"(^|[;&|]\s*)(rm|rmdir|shred|sudo)\b", stripped, re.I):
        return "deny", "destructive command blocked"
    if re.search(r"(curl|wget)\b[^\n]*\|\s*(ba)?sh\b", stripped, re.I):
        return "deny", "pipe-to-shell download blocked"
    if re.search(r"\b(DROP|TRUNCATE)\b", stripped):
        return "deny", "dangerous SQL keyword blocked"
    if re.search(r"(^|[;&|]\s*)(chmod|chown)\b", stripped, re.I):
        return "deny", "chmod/chown blocked"

    lower = stripped.lower()
    promote_wrapper = (
        "./tools/agent/promote-spec-kit-feature",
        "tools/agent/promote-spec-kit-feature",
        "bash tools/agent/promote-spec-kit-feature",
    )
    if any(lower.startswith(p) for p in promote_wrapper):
        return "allow", "controlled promote-spec-kit-feature wrapper"
    if "promote_spec_kit_feature.py" in lower:
        return "deny", "use tools/agent/promote-spec-kit-feature wrapper only"

    for prefix in (
        "./tools/agent/",
        "tools/agent/",
        "bash tools/agent/",
        "python3 tools/agent/",
        "python3 tools/agent-policy/",
        "python3 .cursor/hooks/health-check.py",
    ):
        if lower.startswith(prefix):
            return "allow", "approved wrapper"

    for prefix in TEST_BUILD_PREFIXES:
        if lower.startswith(prefix.lower()):
            for tgt in _extract_redirect_targets(stripped):
                rel = norm_path(tgt, repo)
                perm, reason = _shell_target_allowed(rel, state, repo)
                if perm != "allow":
                    return "deny", f"test/build redirect blocked: {reason}"
            return "allow", "test/build command"

    for prefix in READ_ONLY_PREFIXES:
        if lower == prefix.strip() or lower.startswith(prefix):
            if EXPLICIT_WRITE_CMDS.search(stripped) or _extract_redirect_targets(stripped):
                break
            return "allow", "read-only command"

    targets = _extract_redirect_targets(stripped) + _targets_from_tee(stripped)
    mutating = bool(
        EXPLICIT_WRITE_CMDS.search(stripped)
        or HEREDOC_RE.search(stripped)
        or targets
        or _looks_like_interpreter_write(stripped)
    )

    if mutating:
        if _looks_like_interpreter_write(stripped):
            return "deny", (
                "Shell interpreter write blocked; use structured Write/Edit within allowed_paths "
                "(Shell must not bypass Write/Edit controls)"
            )
        for tgt in targets:
            rel = norm_path(tgt, repo)
            if _is_shell_blocked_spec_target(rel):
                return "deny", "Shell write to Spec Kit canonical paths blocked"
            perm, reason = _shell_target_allowed(rel, state, repo)
            if perm != "allow":
                return "deny", f"Shell write blocked ({reason})"
        if re.search(r"\bsed\s+-i\b|\bperl\s+-pi?\b", stripped, re.I):
            for tok in stripped.split():
                if tok.startswith("-") or tok in {"sed", "perl"}:
                    continue
                if "/" in tok or tok.endswith((".py", ".md", ".json", ".ts", ".tsx", ".java")):
                    rel = norm_path(tok, repo)
                    perm, reason = _shell_target_allowed(rel, state, repo)
                    if perm != "allow":
                        return "deny", f"in-place Shell edit blocked ({reason})"
        if targets or EXPLICIT_WRITE_CMDS.search(stripped):
            # If we couldn't prove a safe target for an explicit writer, deny.
            if not targets and EXPLICIT_WRITE_CMDS.search(stripped):
                return "deny", "explicit Shell write tool without clear allowed target"
            return "allow", "shell write within policy"

    if re.search(r"(>|>>)\s*/(?!home/suso/debina\b)", stripped) or re.search(r"\bcd\s+/", stripped):
        return "ask", "ambiguous path outside workspace"

    return "allow", "default allow with post-check"


def unauthorized_new_paths(
    before: set[str],
    after: set[str],
    *,
    state: ActiveState | None,
    repo: Path,
) -> list[str]:
    bad: list[str] = []
    for rel in sorted(after - before):
        if is_generated_output(rel) or is_gitignored(repo, rel):
            continue
        if state is None:
            if not rel.startswith("work/"):
                bad.append(rel)
            continue
        perm, _ = evaluate_write(rel, tool="Shell", state=state, repo=repo)
        if perm != "allow":
            bad.append(rel)
    return bad


def state_dir(repo: Path) -> Path:
    h = abs(hash(str(repo.resolve()))) % (10**12)
    d = Path("/tmp") / f"debina-hook-state-{h}"
    d.mkdir(parents=True, exist_ok=True)
    return d

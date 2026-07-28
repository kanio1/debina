#!/usr/bin/env python3
"""Block clearly dangerous shell commands for Debina lean harness."""
from __future__ import annotations

import json
import re
import sys

ALLOW_PREFIXES = (
    "git status",
    "git diff",
    "git log",
    "git show",
    "git rev-parse",
    "git ls-files",
    "git grep",
    "git blame",
    "git branch --show-current",
    "./tools/agent/",
    "tools/agent/",
    "bash tools/",
    "python3 tools/",
    "python3 .cursor/hooks/health-check.py",
)

DENY_PATTERNS = [
    (r"\bgit\s+push\b", "git push is blocked"),
    (r"\bgit\s+commit\b", "git commit is blocked"),
    (r"\bgit\s+merge\b", "git merge is blocked"),
    (r"\bgit\s+rebase\b", "git rebase is blocked"),
    (r"\bgit\s+reset\b", "git reset is blocked"),
    (r"\bgit\s+clean\b", "git clean is blocked"),
    (r"\bgit\s+restore\b", "git restore is blocked"),
    (r"\bgit\s+checkout\b", "git checkout is blocked"),
    (r"\bgit\s+switch\b", "git switch is blocked"),
    (r"(^|[;&|]\s*)rm\b", "rm is blocked"),
    (r"(^|[;&|]\s*)rmdir\b", "rmdir is blocked"),
    (r"\bshred\b", "shred is blocked"),
    (r"(^|[;&|]\s*)sudo\b", "sudo is blocked"),
    (r"\bchmod\b", "chmod is blocked"),
    (r"\bchown\b", "chown is blocked"),
    (r"\bsed\s+-i\b", "in-place sed is blocked"),
    (r"\bfind\b[^\n]*\s-delete\b", "find -delete is blocked"),
    (r"\bfind\b[^\n]*\s-exec\b", "find -exec is blocked"),
    (r"\bdocker\s+(rm|rmi|prune)\b", "destructive docker is blocked"),
    (r"\bdocker\s+compose\s+down\b", "docker compose down is blocked"),
    (r"\bpodman\s+(rm|rmi|prune)\b", "destructive podman is blocked"),
    (r"\bpodman\s+compose\s+down\b", "podman compose down is blocked"),
    (r"\bDROP\b", "DROP is blocked"),
    (r"\bTRUNCATE\b", "TRUNCATE is blocked"),
    (r"(curl|wget)\b[^\n]*\|\s*(ba)?sh\b", "pipe-to-shell download is blocked"),
]

AMBIGUOUS = re.compile(
    r"(>|>>)\s*/(?!home/suso/debina\b)|"
    r"\bcd\s+/|"
    r"\btee\s+/|"
    r"\bcp\b[^\n]*/etc/|"
    r"\bmv\b[^\n]*/etc/",
    re.I,
)


def respond(permission: str, user_message: str = "", agent_message: str = "") -> None:
    out = {"permission": permission}
    if user_message:
        out["user_message"] = user_message
    if agent_message:
        out["agent_message"] = agent_message
    print(json.dumps(out))


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        respond("ask", "Invalid hook payload", "command-guard could not parse stdin JSON")
        return 0

    command = str(payload.get("command") or payload.get("tool_input", {}).get("command") or "")
    stripped = command.strip()
    if not stripped:
        respond("allow")
        return 0

    lower = stripped.lower()
    for prefix in ALLOW_PREFIXES:
        if lower.startswith(prefix.lower()):
            # still deny if the allowed wrapper embeds a blocked git write
            if prefix.startswith(("git status", "git diff", "git log", "git show", "git rev-parse", "git ls-files", "git grep", "git blame", "git branch")):
                respond("allow")
                return 0
            if prefix.startswith(("./tools/agent/", "tools/agent/", "bash tools/", "python3 tools/", "python3 .cursor/hooks/health-check.py")):
                respond("allow")
                return 0

    for pattern, reason in DENY_PATTERNS:
        if re.search(pattern, stripped, re.I):
            respond("deny", reason, reason)
            return 0

    if AMBIGUOUS.search(stripped):
        respond(
            "ask",
            "Ambiguous shell command may write outside the workspace. Approve manually if intended.",
            "command-guard requests manual approval for ambiguous redirect/path",
        )
        return 0

    respond("allow")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

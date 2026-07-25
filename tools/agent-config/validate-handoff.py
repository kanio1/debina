#!/usr/bin/env python3
"""Validate structural shape of root HANDOFF.md.

Objective structure only — does not judge whether status is factually current.
Diagnostic IDs: HANDOFF-001 through HANDOFF-010.

Usage:
  python3 tools/agent-config/validate-handoff.py
  python3 tools/agent-config/validate-handoff.py --path HANDOFF.md
  python3 tools/agent-config/validate-handoff.py --self-test
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_HANDOFF = REPO_ROOT / "HANDOFF.md"
FIXTURES = Path(__file__).resolve().parent / "fixtures" / "handoff"

REQUIRED_SECTIONS = [
    "Current objective",
    "Current use case",
    "Current state",
    "Completed",
    "Decisions",
    "Blocked for production",
    "Next tasks",
    "Resume from here",
]

# Allowed phase/verdict/working_tree enums must stay aligned with
# .cursor/rules/25-handoff.mdc and .claude/skills/session-handoff/SKILL.md.
WORKFLOW_PHASES = {
    "DISCOVERY",
    "READINESS_BLOCKED",
    "READY_TO_PLAN",
    "PLANNED",
    "IMPLEMENTING",
    "VERIFYING",
    "READY_FOR_REVIEW",
    "FIXING_REVIEW",
    "READY_TO_COMMIT",
}

READINESS_VERDICTS = {
    "READY",
    "BLOCKED",
    "HUMAN_REVIEW_REQUIRED",
    "NOT_APPLICABLE",
}

WORKING_TREE = {"clean", "modified", "staged"}

SECTION_RE = re.compile(r"^## (.+)\s*$", re.MULTILINE)
TASK_RE = re.compile(r"^\s*\d+\.\s+\S", re.MULTILINE)
YAML_FENCE_RE = re.compile(
    r"## Current state\s*\n+```ya?ml\s*\n(.*?)\n```",
    re.DOTALL | re.IGNORECASE,
)
YAML_KEY_RE = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*)\s*:\s*(.*)$", re.MULTILINE)


class Finding:
    def __init__(self, code: str, message: str) -> None:
        self.code = code
        self.message = message

    def __str__(self) -> str:
        return f"{self.code}: {self.message}"


def parse_sections(text: str) -> list[tuple[str, int]]:
    return [(m.group(1).strip(), m.start()) for m in SECTION_RE.finditer(text)]


def section_body(text: str, title: str, sections: list[tuple[str, int]]) -> str:
    titles = [name for name, _ in sections]
    if title not in titles:
        return ""
    idx = titles.index(title)
    start = sections[idx][1]
    header_end = text.find("\n", start)
    if header_end < 0:
        return ""
    end = sections[idx + 1][1] if idx + 1 < len(sections) else len(text)
    return text[header_end + 1 : end].strip()


def parse_checkpoint_yaml(block: str) -> dict[str, str]:
    values: dict[str, str] = {}
    for match in YAML_KEY_RE.finditer(block):
        key = match.group(1)
        raw = match.group(2).strip()
        if raw.startswith(("'", '"')) and raw.endswith(("'", '"')) and len(raw) >= 2:
            raw = raw[1:-1]
        values[key] = raw
    return values


def validate_handoff(text: str, *, path_label: str = "HANDOFF.md") -> list[Finding]:
    findings: list[Finding] = []

    if not text.strip():
        findings.append(Finding("HANDOFF-001", f"{path_label} is empty"))
        return findings

    sections = parse_sections(text)
    names = [name for name, _ in sections]

    if len(names) == 0:
        findings.append(Finding("HANDOFF-001", f"{path_label} has no ## sections"))
        return findings

    if names != REQUIRED_SECTIONS:
        if set(names) != set(REQUIRED_SECTIONS) or len(names) != 8:
            extra = [n for n in names if n not in REQUIRED_SECTIONS]
            missing = [n for n in REQUIRED_SECTIONS if n not in names]
            if len(names) > 8 or extra:
                findings.append(
                    Finding(
                        "HANDOFF-004",
                        f"extra second-level section(s): {extra or names}",
                    )
                )
            if missing:
                findings.append(
                    Finding("HANDOFF-002", f"missing required section(s): {missing}")
                )
            if not missing and not extra and names != REQUIRED_SECTIONS:
                findings.append(
                    Finding(
                        "HANDOFF-003",
                        f"sections out of order: {names}",
                    )
                )
        else:
            findings.append(
                Finding("HANDOFF-003", f"sections out of order: {names}")
            )

    if "Next tasks" in names:
        body = section_body(text, "Next tasks", sections)
        tasks = TASK_RE.findall(body)
        count = len(tasks)
        if count < 3 or count > 5:
            findings.append(
                Finding(
                    "HANDOFF-005",
                    f"Next tasks must contain 3–5 immediate tasks, found {count}",
                )
            )

    if "Resume from here" in names:
        resume = section_body(text, "Resume from here", sections)
        if not resume:
            findings.append(Finding("HANDOFF-006", "Resume from here is empty"))

    if "Current use case" in names:
        use_case = section_body(text, "Current use case", sections)
        if not use_case:
            findings.append(Finding("HANDOFF-007", "Current use case is empty"))

    fence = YAML_FENCE_RE.search(text)
    if fence:
        values = parse_checkpoint_yaml(fence.group(1))
        phase = values.get("workflow_phase")
        if phase is not None and phase not in WORKFLOW_PHASES:
            findings.append(
                Finding(
                    "HANDOFF-008",
                    f"invalid workflow_phase: {phase!r}",
                )
            )
        verdict = values.get("readiness_verdict")
        if verdict is not None and verdict not in READINESS_VERDICTS:
            findings.append(
                Finding(
                    "HANDOFF-008",
                    f"invalid readiness_verdict: {verdict!r}",
                )
            )
        tree = values.get("working_tree")
        if tree is not None and tree not in WORKING_TREE:
            findings.append(
                Finding(
                    "HANDOFF-009",
                    f"invalid working_tree: {tree!r}",
                )
            )
        next_action = values.get("next_action", "").strip()
        if "next_action" in values and not next_action:
            findings.append(Finding("HANDOFF-009", "next_action is empty"))
        if next_action and "Resume from here" in names:
            resume = section_body(text, "Resume from here", sections)
            resume_line = next(
                (line.strip() for line in resume.splitlines() if line.strip()),
                "",
            )
            if resume_line and resume_line != next_action:
                findings.append(
                    Finding(
                        "HANDOFF-010",
                        "Resume from here must match next_action when checkpoint YAML is present",
                    )
                )

    return findings


def run_self_test() -> int:
    cases = [
        ("valid.md", []),
        ("valid-none-use-case.md", []),
        ("missing-section.md", ["HANDOFF-002"]),
        ("wrong-order.md", ["HANDOFF-003"]),
        ("ninth-section.md", ["HANDOFF-004"]),
        ("two-tasks.md", ["HANDOFF-005"]),
        ("six-tasks.md", ["HANDOFF-005"]),
        ("empty-resume.md", ["HANDOFF-006"]),
        ("invalid-phase.md", ["HANDOFF-008"]),
        ("invalid-readiness-verdict.md", ["HANDOFF-008"]),
        ("resume-mismatch.md", ["HANDOFF-010"]),
    ]
    failed = 0
    for name, expected_codes in cases:
        path = FIXTURES / name
        findings = validate_handoff(path.read_text(encoding="utf-8"), path_label=name)
        codes = sorted({f.code for f in findings})
        want = sorted(set(expected_codes))
        if codes != want:
            print(f"SELF-TEST FAIL {name}: got {codes}, want {want}")
            for finding in findings:
                print(f"  {finding}")
            failed += 1
        else:
            print(f"SELF-TEST OK    {name} -> {codes or ['PASS']}")
    return 1 if failed else 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--path", type=Path, default=DEFAULT_HANDOFF)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)

    if args.self_test:
        return run_self_test()

    path: Path = args.path
    if not path.is_file():
        print(f"HANDOFF-001: {path} does not exist")
        return 1

    findings = validate_handoff(path.read_text(encoding="utf-8"), path_label=str(path))
    if findings:
        for finding in findings:
            print(f"FAIL  {finding}")
        return 1

    print(f"OK    {path} structural handoff checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())

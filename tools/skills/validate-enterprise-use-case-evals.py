#!/usr/bin/env python3
"""Deterministic contract evals; does not claim implicit model-routing proof."""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SKILL = "enterprise-use-case-engineering"
BASE = ROOT / "tools/skills/evals"


def load(relative: str) -> dict:
    path = BASE / relative
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise AssertionError(f"{path.relative_to(ROOT)}: cannot parse: {exc}") from exc


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def unique_ids(cases: list[dict], label: str) -> None:
    ids = [case.get("id") for case in cases]
    require(all(ids), f"{label}: every case needs id")
    require(len(ids) == len(set(ids)), f"{label}: duplicate case id")


def main() -> int:
    try:
        routing = load("routing/enterprise-use-case-engineering.yaml")
        regression = load("regression/enterprise-use-case-engineering.json")
        adversarial = load("adversarial/enterprise-use-case-engineering.json")
        e2e = load("e2e/enterprise-use-case-engineering.json")

        for label, fixture in (
            ("routing", routing),
            ("regression", regression),
            ("adversarial", adversarial),
            ("e2e", e2e),
        ):
            require(fixture.get("skill") == SKILL, f"{label}: wrong skill")

        scenarios = routing["scenarios"]
        unique_ids(scenarios, "routing")
        positive = [case for case in scenarios if case["kind"] == "positive"]
        negative = [case for case in scenarios if case["kind"] == "negative"]
        require(len(positive) >= 10, "routing: fewer than ten positive cases")
        require(len(negative) >= 10, "routing: fewer than ten negative cases")
        for case in positive:
            require(SKILL in case["expected"], f"routing {case['id']}: skill must activate")
            require(case.get("first") and case.get("forbidden") and case.get("evidence"),
                    f"routing {case['id']}: incomplete expectation")
        for case in negative:
            require(SKILL in case["must_not"], f"routing {case['id']}: skill must not activate")
            require("NO_USE_CASE_CHANGE" in case.get("evidence", []),
                    f"routing {case['id']}: missing NO_USE_CASE_CHANGE evidence")

        regression_cases = regression["cases"]
        unique_ids(regression_cases, "regression")
        required_regressions = {
            "valid-new-use-case", "new-alternative-flow", "private-refactor",
            "quality-scenario", "missing-source", "rail-leakage",
            "aggregate-invention", "technical-story-without-slice",
            "project-policy-versus-scheme", "implementation-not-authority",
        }
        require(required_regressions <= {case["id"] for case in regression_cases},
                "regression: mandatory case missing")
        require(all(case.get("expected") and case.get("forbidden")
                    for case in regression_cases), "regression: incomplete expected/forbidden")

        adversarial_cases = adversarial["cases"]
        unique_ids(adversarial_cases, "adversarial")
        require(len(adversarial_cases) >= 12, "adversarial: fewer than twelve cases")
        require(all(case.get("fail_closed") is True and case.get("expected")
                    and case.get("forbidden") for case in adversarial_cases),
                "adversarial: every case must fail closed")

        require(e2e["chain"] == [
            "enterprise-use-case-engineering",
            "source-backed-payments-modeling",
            "architecture-evolution-review",
            "planning-semantic-integrity",
        ], "e2e: cross-skill chain order changed")
        required_outputs = {
            "external actor goal", "source gaps", "business rules",
            "file/group/instruction distinction", "parent use case", "main flow",
            "alternative flows", "behavioral slices", "AGGREGATE_REVIEW_REQUIRED",
            "modules/ports/events", "data ownership", "security context",
            "quality scenarios", "epic/story proposal", "executable verify",
            "human review state",
        }
        require(required_outputs <= set(e2e["required_outputs"]),
                "e2e: required handoff output missing")
        require("implement feature" in e2e["forbidden"], "e2e: feature implementation not forbidden")

        skill_text = (ROOT / regression["path"]).read_text(encoding="utf-8").lower()
        for phrase in regression["contains"]:
            require(phrase.lower() in skill_text, f"skill contract missing phrase: {phrase}")

        print(f"ROUTING-CONTRACT: PASS positive={len(positive)} negative={len(negative)}")
        print(f"REGRESSION-CONTRACT: PASS cases={len(regression_cases)}")
        print(f"ADVERSARIAL-CONTRACT: PASS cases={len(adversarial_cases)}")
        print("CROSS-SKILL-CHAIN: PASS stages=4 required_outputs=16")
        print("ASSURANCE: deterministic fixture/contract proof; implicit model routing NOT_EXECUTED")
        return 0
    except (AssertionError, KeyError, TypeError) as exc:
        print(f"ENTERPRISE USE-CASE EVALS: FAIL: {exc}")
        return 1


if __name__ == "__main__":
    sys.exit(main())

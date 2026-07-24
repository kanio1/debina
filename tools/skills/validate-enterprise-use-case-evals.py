#!/usr/bin/env python3
"""Deterministic contract evals; does not claim implicit model-routing proof."""
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
SKILL = "enterprise-use-case-engineering"
BASE = ROOT / "tools/skills/evals"
VOCABULARY = (
    ROOT
    / "docs/governance/methodology-assurance/CLASSIFICATION-VOCABULARY.yaml"
)
NON_CLASSIFICATION_MACHINE_TOKENS = {"FIXTURE_CONTRACT"}


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
        vocabulary_result = subprocess.run(
            [
                sys.executable,
                str(ROOT / "tools/governance/validate-classification-vocabulary.py"),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        require(
            vocabulary_result.returncode == 0,
            f"classification vocabulary failed:\n{vocabulary_result.stdout}",
        )
        vocabulary = yaml.safe_load(VOCABULARY.read_text(encoding="utf-8"))
        canonical = {
            item["value"] for item in vocabulary["classifications"]
        }
        deprecated = {
            alias
            for item in vocabulary["classifications"]
            for alias in item.get("deprecated_aliases", [])
        }
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

        def assert_fixture_tokens(value: object, label: str) -> None:
            if isinstance(value, dict):
                for nested in value.values():
                    assert_fixture_tokens(nested, label)
            elif isinstance(value, list):
                for nested in value:
                    assert_fixture_tokens(nested, label)
            elif isinstance(value, str):
                if value in deprecated:
                    raise AssertionError(f"{label}: deprecated classification {value}")
                if (
                    ("_" in value or "-" in value)
                    and value.upper() == value
                    and " " not in value
                    and value not in canonical
                    and value not in NON_CLASSIFICATION_MACHINE_TOKENS
                ):
                    raise AssertionError(f"{label}: unknown machine token {value}")

        for label, fixture in (
            ("routing", routing),
            ("regression", regression),
            ("adversarial", adversarial),
            ("cross-skill-chain", e2e),
        ):
            assert_fixture_tokens(fixture, label)

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
        registry = json.loads(
            (ROOT / "planning/skills/skills-registry.yaml").read_text(encoding="utf-8")
        )
        active_skills = {
            item["name"] for item in registry["skills"] if item["status"] == "ACTIVE"
        }
        require(
            set(e2e["chain"]) <= active_skills,
            "cross-skill chain contains a non-ACTIVE registry skill",
        )
        require(
            e2e.get("assurance_level") == "FIXTURE_CONTRACT",
            "cross-skill chain must declare FIXTURE_CONTRACT assurance",
        )
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

        print(f"ROUTING-FIXTURE-CONTRACT: PASS positive={len(positive)} negative={len(negative)}")
        print(f"REGRESSION-FIXTURE-CONTRACT: PASS cases={len(regression_cases)}")
        print(f"ADVERSARIAL-FIXTURE-CONTRACT: PASS cases={len(adversarial_cases)}")
        print("CROSS-SKILL-CHAIN-FIXTURE-CONTRACT: PASS stages=4 required_outputs=16")
        print(
            'MODEL-BEHAVIOR: NOT_EXECUTED '
            'reason="no approved safe model evaluator exposes reliable skill-selection evidence"'
        )
        return 0
    except (AssertionError, KeyError, TypeError) as exc:
        print(f"ENTERPRISE USE-CASE EVALS: FAIL: {exc}")
        return 1


if __name__ == "__main__":
    sys.exit(main())

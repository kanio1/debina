#!/usr/bin/env python3
from __future__ import annotations

import argparse
import copy
import json
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
VOCABULARY = (
    ROOT
    / "docs/governance/methodology-assurance/CLASSIFICATION-VOCABULARY.yaml"
)
TOKEN = re.compile(r"^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+$|^(?:READY|BLOCKED)$")
CONTRACT = re.compile(
    r"## Classification contract\s+```yaml\s+(.*?)\s+```", re.S
)


def emit(code: str, location: str, message: str) -> None:
    print(f"ERROR {code} {location}: {message}")


def load_contract(path: Path) -> dict[str, object]:
    match = CONTRACT.search(path.read_text(encoding="utf-8"))
    if not match:
        raise ValueError("classification contract block missing")
    data = yaml.safe_load(match.group(1))
    if not isinstance(data, dict):
        raise ValueError("classification contract must be a mapping")
    return data


def repository_state() -> tuple[dict, dict[str, dict], list[tuple[str, str]], str]:
    vocabulary = yaml.safe_load(VOCABULARY.read_text(encoding="utf-8"))
    contracts: dict[str, dict] = {}
    for skill, relative in vocabulary.get("skill_contract_files", {}).items():
        contracts[skill] = load_contract(ROOT / relative)
    active_text = [
        (relative, (ROOT / relative).read_text(encoding="utf-8"))
        for relative in vocabulary.get("active_machine_token_files", [])
    ]
    return vocabulary, contracts, active_text, str(
        vocabulary.get("model_behavior", {}).get("status", "")
    )


def apply_fixture(
    fixture: dict,
    vocabulary: dict,
    contracts: dict[str, dict],
    active_text: list[tuple[str, str]],
    model_status: str,
) -> tuple[dict, dict[str, dict], list[tuple[str, str]], str]:
    mutation = fixture.get("mutation", {})
    kind = mutation.get("kind")
    value = mutation.get("value")
    if kind == "duplicate_canonical":
        item = copy.deepcopy(vocabulary["classifications"][0])
        item["value"] = value or item["value"]
        vocabulary["classifications"].append(item)
    elif kind == "duplicate_alias":
        vocabulary["classifications"][0]["deprecated_aliases"].append(value)
        vocabulary["classifications"][1]["deprecated_aliases"].append(value)
    elif kind == "alias_collision":
        vocabulary["classifications"][0]["deprecated_aliases"].append(value)
    elif kind == "unknown_producer":
        vocabulary["classifications"][0]["produced_by"].append(value)
    elif kind == "unknown_consumer":
        vocabulary["classifications"][0]["consumed_by"].append(value)
    elif kind == "contract_token":
        contracts[mutation["skill"]][mutation["field"]].append(value)
    elif kind == "active_alias":
        active_text.append((mutation["path"], value))
    elif kind == "unproduced_consumed":
        producer = mutation["producer"]
        consumer = mutation["consumer"]
        contracts[producer]["produces"].remove(value)
        contracts[consumer]["consumes"].append(value)
    elif kind == "model_behavior_pass":
        model_status = "PASS"
    elif kind not in {None, "none"}:
        raise ValueError(f"unknown fixture mutation {kind}")
    return vocabulary, contracts, active_text, model_status


def validate(
    vocabulary: dict,
    contracts: dict[str, dict],
    active_text: list[tuple[str, str]],
    model_status: str,
    location: str,
) -> int:
    errors = 0
    definitions = vocabulary.get("classifications", [])
    values = [item.get("value") for item in definitions]
    aliases = [
        alias
        for item in definitions
        for alias in item.get("deprecated_aliases", [])
    ]
    known_skills = set(vocabulary.get("skill_contract_files", {}))

    def fail(code: str, message: str) -> None:
        nonlocal errors
        errors += 1
        emit(code, location, message)

    if len(values) != len(set(values)):
        fail("CV-001", "duplicate canonical classification value")
    if len(aliases) != len(set(aliases)):
        fail("CV-002", "duplicate deprecated alias")
    collision = set(values) & set(aliases)
    if collision:
        fail("CV-003", f"alias collides with canonical value {sorted(collision)[0]}")
    for item in definitions:
        value = item.get("value", "")
        if not TOKEN.fullmatch(value):
            fail("CV-011", f"noncanonical value syntax {value}")
        for producer in item.get("produced_by", []):
            if producer not in known_skills:
                fail("CV-004", f"{value} has unknown producer {producer}")
        for consumer in item.get("consumed_by", []):
            if consumer not in known_skills:
                fail("CV-005", f"{value} has unknown consumer {consumer}")

    canonical = set(values)
    deprecated = set(aliases)
    actual_producers: dict[str, set[str]] = {value: set() for value in canonical}
    for skill, contract in contracts.items():
        if contract.get("vocabulary") != str(VOCABULARY.relative_to(ROOT)):
            fail("CV-013", f"{skill} does not reference canonical vocabulary")
        for field in ("produces", "consumes"):
            for token in contract.get(field, []):
                if token in deprecated:
                    fail("CV-006", f"{skill} {field} deprecated alias {token}")
                elif token not in canonical:
                    fail("CV-007", f"{skill} {field} unknown token {token}")
        for token in contract.get("produces", []):
            if token in canonical:
                actual_producers[token].add(skill)
                definition = next(item for item in definitions if item["value"] == token)
                if skill not in definition.get("produced_by", []):
                    fail("CV-014", f"{skill} is not declared producer of {token}")
        for token in contract.get("consumes", []):
            if token in canonical:
                definition = next(item for item in definitions if item["value"] == token)
                if skill not in definition.get("consumed_by", []):
                    fail("CV-015", f"{skill} is not declared consumer of {token}")

    for skill, contract in contracts.items():
        for token in contract.get("consumes", []):
            if token in canonical and not actual_producers.get(token):
                fail("CV-009", f"{skill} consumes {token} but no skill produces it")

    for path, text in active_text:
        for alias in deprecated:
            if re.search(rf"(?<![A-Z0-9_-]){re.escape(alias)}(?![A-Z0-9_-])", text):
                fail("CV-008", f"deprecated alias {alias} in active artifact {path}")
                break

    by_value = {item["value"]: item for item in definitions}
    if by_value.get("SOURCE_CONFIRMED", {}).get("blocking_readiness") is not False:
        fail("CV-012", "SOURCE_CONFIRMED must not block readiness")
    for token in (
        "VERIFY_PER_USE",
        "SOURCE_BLOCKED",
        "INSUFFICIENT_EVIDENCE",
        "CONFLICTING_SOURCES",
        "PARTICIPANT_DOCUMENTATION_REQUIRED",
    ):
        if by_value.get(token, {}).get("blocking_readiness") is not True:
            fail("CV-012", f"{token} must block readiness")
    if model_status == "PASS":
        fail("CV-010", "MODEL-BEHAVIOR PASS is forbidden without an approved runner proof")

    if not errors:
        print(
            "CLASSIFICATION-VOCABULARY: PASS "
            f"canonical={len(canonical)} aliases={len(deprecated)} skills={len(contracts)}"
        )
        print("CROSS-SKILL-CLASSIFICATION-CONTRACT: PASS stages=4")
    return 1 if errors else 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture", type=Path)
    args = parser.parse_args()
    try:
        vocabulary, contracts, active_text, model_status = repository_state()
        location = str(VOCABULARY.relative_to(ROOT))
        if args.fixture:
            fixture = json.loads(args.fixture.read_text(encoding="utf-8"))
            vocabulary, contracts, active_text, model_status = apply_fixture(
                fixture,
                copy.deepcopy(vocabulary),
                copy.deepcopy(contracts),
                list(active_text),
                model_status,
            )
            location = str(args.fixture)
        return validate(vocabulary, contracts, active_text, model_status, location)
    except (OSError, ValueError, KeyError, TypeError, yaml.YAMLError) as exc:
        emit("CV-000", str(args.fixture or VOCABULARY), str(exc))
        return 1


if __name__ == "__main__":
    sys.exit(main())

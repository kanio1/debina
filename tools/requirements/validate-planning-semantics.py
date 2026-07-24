#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

import yaml

from validation_common import ROOT, diagnostic, finish, ids_from_catalog, list_value, story_blocks

BLOCKING_SOURCE = {
    "SOURCE_BLOCKED",
    "INSUFFICIENT_EVIDENCE",
    "CONFLICTING_SOURCES",
    "PARTICIPANT_DOCUMENTATION_REQUIRED",
    "VERIFY_PER_USE",
}
SOURCE_CLASSIFICATIONS = {
    "SOURCE_CONFIRMED",
    "PROJECT_INTERPRETATION",
    "PROJECT_SIMULATION",
    "RAIL_SPECIFIC",
    *BLOCKING_SOURCE,
}
PLACEHOLDER = re.compile(
    r"\b(?:TODO|TBD|PLACEHOLDER|NOT RUN|SKIP FOR NOW|IMPLEMENT LATER)\b|"
    r"^\s*(?:echo\s+true|true|pass)\s*$",
    re.I,
)


def catalog_records(path: Path, prefix: str) -> dict[str, str]:
    records: dict[str, str] = {}
    for block in re.split(r"(?=^\s{2}- )", path.read_text(encoding="utf-8"), flags=re.M):
        match = re.search(rf"\bid:\s*({prefix}-[A-Z0-9-]+)", block)
        if match:
            records[match.group(1)] = block
    return records


def evidence_records(
    root: Path, overrides: list[dict[str, object]] | None = None
) -> dict[str, dict[str, object]]:
    path = root / "docs/standards/SOURCE-EVIDENCE-CATALOG.yaml"
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    registry_data = yaml.safe_load(
        (root / "docs/standards/SOURCE-REGISTRY.yaml").read_text(encoding="utf-8")
    )
    registry = {
        item["id"]: item for item in registry_data.get("sources", [])
    }
    records: dict[str, dict[str, object]] = {}
    entries = list(data.get("evidence", []))
    for override in overrides or []:
        entries = [item for item in entries if item.get("id") != override.get("id")]
        entries.append(override)
    for item in entries:
        source_id = str(item.get("source_registry_id", ""))
        authority = registry.get(source_id, {})
        records[str(item["id"])] = {
            "version": str(item.get("document_version", "")),
            "rules": set(item.get("applicable_rules", []) or []),
            "status": str(item.get("evidence_status", "")),
            "superseded_by": str(item.get("superseded_by", "")),
            "method": str(item.get("verification_method", "")),
            "source_registry_id": source_id,
            "source_tag": str(authority.get("tag", "")),
            "source_authority": str(authority.get("authority", "")),
            "restriction": str(item.get("public_or_restricted", "")),
        }
    return records


def reference_data(
    root: Path, evidence_overrides: list[dict[str, object]] | None = None
) -> dict[str, object]:
    use_cases = catalog_records(root / "docs/requirements/USE-CASE-CATALOG.yaml", "UC")
    processes = ids_from_catalog(root / "docs/requirements/BUSINESS-PROCESS-CATALOG.yaml", "BP")
    rules = ids_from_catalog(root / "docs/requirements/BUSINESS-RULE-CATALOG.yaml", "BR")
    quality_text = (
        root / "docs/architecture/QUALITY-SCENARIO-CATALOG.yaml"
    ).read_text(encoding="utf-8")
    quality = set(re.findall(r"\bscenario_id:\s*(QS-[A-Z0-9-]+)", quality_text))
    module_text = (root / "docs/architecture/MODULE-CATALOG.yaml").read_text(encoding="utf-8")
    modules = set(re.findall(r"^\s*- \{name:\s*([^,]+)", module_text, re.M))
    uc_slices: dict[str, set[str]] = {}
    uc_status: dict[str, str] = {}
    for identifier, line in use_cases.items():
        slices = re.search(r"\bslices:\s*\[([^]]*)\]", line)
        status = re.search(r"\bstatus:\s*([^,}]+)", line)
        uc_slices[identifier] = set(list_value(slices.group(1) if slices else ""))
        uc_status[identifier] = status.group(1).strip() if status else ""
    return {
        "use_cases": set(use_cases),
        "use_case_slices": uc_slices,
        "use_case_status": uc_status,
        "processes": processes,
        "rules": rules,
        "quality": quality,
        "modules": modules,
        "evidence": evidence_records(root, evidence_overrides),
    }


def enforced_readiness_errors(
    meta: dict[str, str], block: str, refs: dict[str, object]
) -> list[tuple[str, str]]:
    errors: list[tuple[str, str]] = []

    def require_value(key: str, code: str, label: str) -> str:
        value = meta.get(key, "").strip().strip("*")
        if not value:
            errors.append((code, f"missing {label}"))
        return value

    use_case = require_value("use_case_id", "ESR-001", "use_case_id")
    slices = list_value(meta.get("slice_id"))
    if not slices:
        errors.append(("ESR-002", "missing slice_id"))
    if use_case and use_case not in refs["use_cases"]:
        errors.append(("ESR-003", f"unknown use_case_id {use_case}"))
    known_slices = refs["use_case_slices"].get(use_case, set())
    for slice_id in slices:
        if slice_id not in known_slices:
            errors.append(("ESR-004", f"slice_id {slice_id} does not belong to {use_case}"))

    process = require_value("business_process_id", "ESR-005", "business_process_id")
    if process and process not in refs["processes"]:
        errors.append(("ESR-005", f"unknown business_process_id {process}"))

    for key, label in (
        ("actor_or_external_system", "actor_or_external_system"),
        ("actor_goal", "actor_goal"),
        ("main_flow", "main_flow"),
    ):
        require_value(key, "ESR-006", label)
    if meta.get("main_flow") and not re.search(r"\bBF-[0-9]+", meta["main_flow"]):
        errors.append(("ESR-006", "main_flow must reference a stable BF-* flow"))

    source_classification = require_value(
        "source_classification", "ESR-007", "source_classification"
    )
    if source_classification and source_classification not in SOURCE_CLASSIFICATIONS:
        errors.append(("ESR-007", f"unsupported source_classification {source_classification}"))

    evidence_ids = list_value(meta.get("source_evidence"))
    if not evidence_ids:
        errors.append(("ESR-008", "missing per-claim source_evidence"))
    evidence = refs["evidence"]
    for evidence_id in evidence_ids:
        if evidence_id not in evidence:
            errors.append(("ESR-008", f"unknown source_evidence {evidence_id}"))

    rules = list_value(meta.get("applicable_rules"))
    if not rules:
        errors.append(("ESR-009", "missing applicable_rules"))
    for rule in rules:
        if rule not in refs["rules"]:
            errors.append(("ESR-009", f"unknown applicable rule {rule}"))
        elif evidence_ids and not any(rule in evidence.get(item, {}).get("rules", set())
                                      for item in evidence_ids):
            errors.append(("ESR-009", f"no per-claim evidence links applicable rule {rule}"))

    owner = require_value("module_owner", "ESR-010", "module_owner")
    if owner and owner not in refs["modules"]:
        errors.append(("ESR-010", f"unknown module_owner {owner}"))

    architecture = require_value(
        "architecture_realization", "ESR-011", "architecture_realization"
    )
    if architecture and PLACEHOLDER.search(architecture):
        errors.append(("ESR-011", "architecture_realization is a placeholder"))

    require_value("security_context", "ESR-012", "security_context")
    scenarios = list_value(meta.get("quality_scenarios"))
    if not scenarios:
        errors.append(("ESR-013", "missing quality_scenarios"))
    for scenario in scenarios:
        if scenario not in refs["quality"]:
            errors.append(("ESR-013", f"unknown quality scenario {scenario}"))

    verify = require_value("executable_verify", "ESR-014", "executable_verify")
    if verify and PLACEHOLDER.search(verify):
        errors.append(("ESR-014", "executable_verify is a placeholder or NOT RUN"))

    readiness = meta.get("readiness", "").strip().strip("*")
    if readiness == "READY":
        if source_classification in BLOCKING_SOURCE:
            errors.append(("ESR-015", f"READY conflicts with {source_classification}"))
        if refs["use_case_status"].get(use_case) == "SOURCE_BLOCKED":
            errors.append(("ESR-015", f"READY references source-blocked use case {use_case}"))
        for evidence_id in evidence_ids:
            record = evidence.get(evidence_id, {})
            evidence_status = str(record.get("status", ""))
            superseded_by = str(record.get("superseded_by", ""))
            project_authority = (
                record.get("source_tag") == "PROJECT-ADR"
                and record.get("method") == "PROJECT_ADR"
                and "project ADR" in str(record.get("source_authority", ""))
            )
            if evidence_status == "STALE" or superseded_by not in {
                "", "NONE", "UNKNOWN"
            }:
                errors.append((
                    "ESR-019",
                    f"evidence {evidence_id} is stale or superseded by {superseded_by}",
                ))
            elif evidence_status in {
                "VERIFY_PER_USE", "INCOMPLETE", "CONFLICTING", "RESTRICTED"
            } and source_classification != "SOURCE_CONFIRMED" and not (
                source_classification == "PROJECT_INTERPRETATION"
                and evidence_status == "INCOMPLETE"
                and project_authority
            ):
                errors.append((
                    "ESR-018",
                    f"evidence {evidence_id} is not VERIFIED ({evidence_status})",
                ))
            if source_classification == "SOURCE_CONFIRMED":
                if evidence_status not in {"VERIFIED", "STALE"}:
                    errors.append((
                        "ESR-018",
                        f"SOURCE_CONFIRMED requires VERIFIED evidence {evidence_id}",
                    ))
                if project_authority:
                    errors.append((
                        "ESR-020",
                        f"SOURCE_CONFIRMED conflicts with project authority {evidence_id}",
                    ))
        if source_classification == "PROJECT_INTERPRETATION":
            project_authorities = [
                evidence_id
                for evidence_id in evidence_ids
                if (
                    evidence.get(evidence_id, {}).get("source_tag") == "PROJECT-ADR"
                    and evidence.get(evidence_id, {}).get("method") == "PROJECT_ADR"
                    and "project ADR"
                    in str(evidence.get(evidence_id, {}).get("source_authority", ""))
                )
            ]
            if not project_authorities:
                errors.append((
                    "ESR-021",
                    "PROJECT_INTERPRETATION evidence "
                    f"{','.join(evidence_ids)} lacks accepted/frozen project authority",
                ))
        if source_classification == "PROJECT_SIMULATION":
            boundary = meta.get("synthetic_boundary", "").strip()
            if not boundary or PLACEHOLDER.search(boundary):
                errors.append((
                    "ESR-022",
                    "PROJECT_SIMULATION evidence "
                    f"{','.join(evidence_ids)} lacks explicit synthetic_boundary",
                ))

    status = meta.get("status", "").strip().strip("*")
    if status == "done" and re.search(r"\bNOT RUN\b", block, re.I):
        errors.append(("ESR-016", "done story contains current NOT RUN"))

    if meta.get("work_type", "").strip() == "TECHNICAL_ONLY" and (use_case or slices):
        errors.append(("ESR-017", "technical-only story must not invent a business use case or slice"))
    return errors


def validate_fixture(path: Path) -> int:
    fixture = json.loads(path.read_text(encoding="utf-8"))
    meta = {key: str(value) for key, value in fixture["meta"].items()}
    refs = reference_data(ROOT, fixture.get("evidence_overrides", []))
    errors = enforced_readiness_errors(meta, fixture.get("body", ""), refs)
    for code, message in errors:
        diagnostic("ERROR", code, path, fixture.get("id", "fixture"), "ENFORCED", message)
    return finish(len(errors), 0, validated_fixtures=1)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture", type=Path)
    args = parser.parse_args()
    if args.fixture:
        return validate_fixture(args.fixture)

    errors = warnings = 0
    stories = list(story_blocks())
    refs = reference_data(ROOT)
    for path, epic, story, block, meta in stories:
        status = meta.get("status", "not-started").strip("*")
        subject = f"{epic}/{story}"
        level = meta.get("semantic_enforcement", "LEGACY")
        unchecked = "- [ ]" in block
        hard = level in {"PILOT", "ENFORCED"}

        def report(code: str, message: str) -> None:
            nonlocal errors, warnings
            if hard:
                errors += 1
                diagnostic("ERROR", code, path.relative_to(ROOT), subject, level, message)
            else:
                warnings += 1
                diagnostic("WARNING", code, path.relative_to(ROOT), subject, level, message)

        if status == "done" and unchecked:
            report("PS-001", "done story has unchecked task")
        if status == "done" and re.search(r"\b(?:Blocker|blocked|NOT RUN)\b", block, re.I):
            report("PS-002", "done story contains unresolved blocker/current NOT RUN marker")
        if status == "blocked" and not (
            meta.get("blocker_classification")
            or re.search(r"\b(?:Blocker|\[[A-Z-]*BLOCKED\])", block, re.I)
        ):
            report("PS-003", "blocked story lacks blocker classification")
        if status == "in-progress" and unchecked and not re.search(r"`verify:\s*[^`]+`", block):
            errors += 1
            diagnostic("ERROR", "PS-004", path.relative_to(ROOT), subject, level,
                       "in-progress story has no executable verify")
        if level == "PILOT" and not meta.get("use_case_slices"):
            errors += 1
            diagnostic("ERROR", "PS-005", path.relative_to(ROOT), subject, level,
                       "trace required")
        if level == "ENFORCED":
            for code, message in enforced_readiness_errors(meta, block, refs):
                errors += 1
                diagnostic("ERROR", code, path.relative_to(ROOT), subject, level, message)

    inventory = json.loads((ROOT / "planning/story-inventory.json").read_text())
    if inventory.get("epic_count") != len(list((ROOT / "planning/epics").glob("EPIC-*.md"))):
        errors += 1
        diagnostic("ERROR", "PS-006", "planning/story-inventory.json", "inventory",
                   "generated-count", "epic count differs from files")
    return finish(errors, warnings, validated_stories=len(stories))


if __name__ == "__main__":
    sys.exit(main())

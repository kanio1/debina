#!/usr/bin/env python3
"""Fail-closed referential and readiness checks for Phase E planning artifacts."""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

import yaml

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/requirements"))
from validation_common import diagnostic, finish  # noqa: E402

REVIEW_QUEUES = {
    "PAYMENTS_DOMAIN_REVIEW",
    "LEGAL_REGULATORY_REVIEW",
    "ISO_MESSAGE_REVIEW",
    "DATABASE_MAPPING_REVIEW",
    "SECURITY_REVIEW",
    "ARCHITECTURE_REVIEW",
    "PRODUCT_REVIEW",
    "QA_REVIEW",
}
SEMANTIC_REVIEW_STATES = {
    "NOT_REVIEWED",
    "REVIEW_IN_PROGRESS",
    "HUMAN_APPROVED",
    "HUMAN_REJECTED",
    "CHANGES_REQUESTED",
}
AUTHORITY_TAGS = {
    "EU-LAW",
    "EPC-SCT",
    "EPC-SCT-INST",
    "ISO20022",
    "TIPS",
    "STEP2",
    "RT1",
    "STET",
    "PROJECT-ADR",
    "PROJECT_SIMULATION",
    "ASSUMPTION",
    "OPEN-QUESTION",
    "PARTICIPANT_DOCUMENTATION_REQUIRED",
}
EXPECTED_E1_STORIES = {
    "EPIC-31/31.2",
    "EPIC-19/19.2",
    "EPIC-19/19.4",
    "EPIC-26/26.3",
    "EPIC-26/26.4",
    "EPIC-24/24.10",
}
EXPECTED_E1_RECORD_SLICES = {
    "EPIC-31/31.2": {"UCS-SCT-002-A", "UCS-SCT-002-B"},
    "EPIC-19/19.2": {"UCS-SCT-002-A", "UCS-SCT-002-B"},
    "EPIC-19/19.4": {"UCS-SCT-002-A", "UCS-SCT-002-B"},
    "EPIC-26/26.3": {"UCS-SCT-002-A"},
    "EPIC-26/26.4": {"UCS-SCT-002-A"},
    "EPIC-24/24.10": {"UCS-SCT-002-A", "UCS-SCT-002-B"},
}
PHASE_E_STATIC_PATHS = (
    "planning/programs/DEBINA-PHASE-E-CONTROLLED-BACKLOG-MIGRATION.md",
    "planning/backlog-change-proposals/phase-e/phase-e-reconciliation.yaml",
    "planning/programs/DEBINA-FRONTEND-CAPABILITY-RECONCILIATION.md",
    "docs/standards/SEPA-SCHEME-COVERAGE-MATRIX.yaml",
    "docs/standards/ISO-20022-MESSAGE-COVERAGE-MATRIX.yaml",
    "docs/standards/CSM-CAPABILITY-MATRIX.yaml",
    "docs/standards/SOURCE-GAP-REGISTER.yaml",
    "docs/standards/SOURCE-CHANGE-REGISTER.yaml",
    "docs/standards/SOURCE-EVIDENCE-CATALOG.yaml",
    "docs/data/ISO-XML-DOMAIN-DATABASE-MAPPING-METHOD.md",
    "docs/data/ISO-XML-DOMAIN-DATABASE-MAPPING-TEMPLATE.yaml",
    "docs/data/mappings/E1-PAIN001-SINGLE-INSTRUCTION-MAPPING.yaml",
    "docs/testing/SEPA-PAYMENT-TEST-ALLOCATION-MATRIX.md",
)


def load_yaml(path: Path) -> dict[str, Any]:
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    return data if isinstance(data, dict) else {}


def catalogs() -> dict[str, Any]:
    use_case_data = load_yaml(ROOT / "docs/requirements/USE-CASE-CATALOG.yaml")
    use_cases = {item["id"]: item for item in use_case_data["use_cases"]}
    slices = {
        slice_id: item["id"]
        for item in use_case_data["use_cases"]
        for slice_id in item.get("slices", [])
    }
    processes = {
        item["id"]
        for item in load_yaml(ROOT / "docs/requirements/BUSINESS-PROCESS-CATALOG.yaml")["processes"]
    }
    rules = {
        item["id"]
        for item in load_yaml(ROOT / "docs/requirements/BUSINESS-RULE-CATALOG.yaml")["rules"]
    }
    sources = {
        item["id"]: item
        for item in load_yaml(ROOT / "docs/standards/SOURCE-REGISTRY.yaml")["sources"]
    }
    evidence = {
        item["id"]: item
        for item in load_yaml(ROOT / "docs/standards/SOURCE-EVIDENCE-CATALOG.yaml")["evidence"]
    }
    modules = {
        item["name"]
        for item in load_yaml(ROOT / "docs/architecture/MODULE-CATALOG.yaml")["modules"]
    }
    quality = {
        item["scenario_id"]
        for item in load_yaml(ROOT / "docs/architecture/QUALITY-SCENARIO-CATALOG.yaml")["scenarios"]
    }
    vocabulary = load_yaml(
        ROOT / "docs/governance/methodology-assurance/CLASSIFICATION-VOCABULARY.yaml"
    )["classifications"]
    classifications = {item["value"]: item for item in vocabulary}
    deprecated = {
        alias
        for item in vocabulary
        for alias in item.get("deprecated_aliases", [])
    }
    architecture = {
        item["value"] for item in vocabulary if "architecture" in item.get("domains", [])
    }
    blocked = {
        item["value"] for item in vocabulary if item.get("blocking_readiness") is True
    }
    inventory = json.loads((ROOT / "planning/story-inventory.json").read_text(encoding="utf-8"))
    stories = {f"{item['epic']}/{item['story']}" for item in inventory["stories"]}
    epics = {item["epic"] for item in inventory["epics"]}
    return {
        "use_cases": use_cases,
        "slices": slices,
        "processes": processes,
        "rules": rules,
        "sources": sources,
        "evidence": evidence,
        "modules": modules,
        "quality": quality,
        "classifications": classifications,
        "deprecated": deprecated,
        "architecture": architecture,
        "blocked": blocked,
        "stories": stories,
        "epics": epics,
    }


def list_field(record: dict[str, Any], key: str) -> list[Any]:
    value = record.get(key, [])
    if value is None:
        return []
    return value if isinstance(value, list) else [value]


def validate_record(
    record: dict[str, Any],
    refs: dict[str, Any],
    location: str,
    ordinal: int,
) -> list[tuple[str, str]]:
    errors: list[tuple[str, str]] = []
    subject = str(
        record.get("epic_story_id")
        or record.get("future_canonical_id")
        or record.get("proposal_id")
        or f"record-{ordinal}"
    )

    def fail(code: str, message: str) -> None:
        errors.append((code, f"{subject}: {message}"))

    use_case = record.get("use_case_id")
    if use_case not in refs["use_cases"]:
        fail("PE-001", f"unknown use_case_id {use_case}")
    slice_ids = list_field(record, "slice_id")
    if not slice_ids:
        fail("PE-002", "slice_id is required")
    for slice_id in slice_ids:
        if re.fullmatch(r"UC-[A-Z0-9-]+/S\d+", str(slice_id)):
            fail("PE-013", f"noncanonical slice syntax {slice_id}")
        elif slice_id not in refs["slices"]:
            fail("PE-002", f"unknown slice_id {slice_id}")
        elif refs["slices"][slice_id] != use_case:
            fail("PE-002", f"slice {slice_id} belongs to {refs['slices'][slice_id]}, not {use_case}")

    process = record.get("business_process_id")
    if process not in refs["processes"]:
        fail("PE-003", f"unknown business_process_id {process}")

    evidence_ids = [str(value) for value in list_field(record, "source_evidence")]
    for evidence_id in evidence_ids:
        if evidence_id not in refs["evidence"]:
            fail("PE-005", f"unknown source_evidence {evidence_id}")
    for source_id in list_field(record, "source_registry_ids"):
        if source_id not in refs["sources"]:
            fail("PE-005", f"unknown source registry ID {source_id}")
    for rule in list_field(record, "applicable_rules"):
        if rule not in refs["rules"]:
            fail("PE-004", f"unknown applicable rule {rule}")
        elif evidence_ids and not any(
            rule in refs["evidence"].get(evidence_id, {}).get("applicable_rules", [])
            for evidence_id in evidence_ids
        ):
            fail("PE-004", f"no claim evidence links applicable rule {rule}")

    for module in list_field(record, "module_owners"):
        if module not in refs["modules"]:
            fail("PE-006", f"unknown module owner {module}")
    for scenario in list_field(record, "quality_scenarios"):
        if scenario not in refs["quality"]:
            fail("PE-007", f"unknown quality scenario {scenario}")

    record_type = record.get("record_type")
    story_id = record.get("epic_story_id")
    if record_type == "CANONICAL_STORY":
        if story_id not in refs["stories"]:
            fail("PE-008", f"canonical story does not exist: {story_id}")
    elif record_type == "CANONICAL_STORY_GROUP":
        canonical_story_ids = list_field(record, "canonical_story_ids")
        if not canonical_story_ids:
            fail("PE-008", "canonical_story_ids are required for a story group")
        for canonical_story_id in canonical_story_ids:
            if canonical_story_id not in refs["stories"]:
                fail("PE-008", f"canonical story does not exist: {canonical_story_id}")
        if story_id:
            fail("PE-008", "story group must use canonical_story_ids, not epic_story_id")
    elif record_type == "STORY_PROPOSAL":
        proposal_id = str(record.get("proposal_id", ""))
        future_id = str(record.get("future_canonical_id", ""))
        if not re.fullmatch(r"PHASE-E1-STORY-PROPOSAL-\d{3}", proposal_id):
            fail("PE-008", f"proposal_id is not explicitly typed: {proposal_id}")
        if not re.fullmatch(r"EPIC-\d+/\d+(?:\.\d+)?[A-Z]?", future_id):
            fail("PE-008", f"invalid future canonical story ID {future_id}")
        elif future_id in refs["stories"]:
            fail("PE-008", f"future canonical story already exists: {future_id}")
        elif future_id.split("/", 1)[0] not in refs["epics"]:
            fail("PE-008", f"future epic does not exist: {future_id}")
        if story_id:
            fail("PE-008", "story proposal must not masquerade as epic_story_id")
    else:
        fail("PE-008", f"unknown record_type {record_type}")

    classification = record.get("source_classification")
    if isinstance(classification, list):
        fail("PE-009", "source_classification must be one scalar")
    elif classification in refs["deprecated"]:
        fail("PE-009", f"deprecated classification alias {classification}")
    elif classification not in refs["classifications"]:
        fail("PE-009", f"unknown source_classification {classification}")
    for tag in list_field(record, "source_tags"):
        if tag not in AUTHORITY_TAGS:
            fail("PE-009", f"source tag is not an authority/discovery tag: {tag}")
        if tag in refs["classifications"]:
            fail("PE-009", f"classification used as source tag: {tag}")

    for outcome in list_field(record, "architecture_outcomes"):
        if outcome in refs["deprecated"]:
            fail("PE-010", f"deprecated architecture alias {outcome}")
        elif outcome not in refs["architecture"]:
            fail("PE-010", f"unknown architecture outcome {outcome}")
    for queue in list_field(record, "review_queues"):
        if queue not in REVIEW_QUEUES:
            fail("PE-011", f"unknown review queue {queue}")

    readiness = record.get("current_readiness")
    if (
        isinstance(classification, str)
        and classification in refs["blocked"]
        and readiness in {"READY", "READY_CANDIDATE"}
    ):
        fail("PE-012", f"{classification} conflicts with current_readiness {readiness}")
    if readiness in {"DECISION_BLOCKED", "SOURCE_BLOCKED", "HUMAN_REVIEW_REQUIRED", "VERIFY_PER_USE"}:
        if not list_field(record, "blocking_decisions"):
            fail("PE-012", f"{readiness} requires blocking_decisions")

    if classification == "SOURCE_CONFIRMED" and readiness == "READY":
        for evidence_id in evidence_ids:
            evidence = refs["evidence"].get(evidence_id, {})
            if (
                evidence.get("evidence_status") != "VERIFIED"
                or evidence.get("semantic_review_state") != "HUMAN_APPROVED"
            ):
                fail(
                    "PE-015",
                    f"SOURCE_CONFIRMED + READY requires VERIFIED + HUMAN_APPROVED evidence {evidence_id}",
                )
    return errors


def load_fixture(path: Path) -> dict[str, Any]:
    fixture = load_yaml(path)
    base = fixture.get("base")
    if not base:
        return fixture
    base_data = load_fixture(path.parent / str(base))
    records = [dict(item) for item in base_data.get("records", [])]
    patch = fixture.get("record_patch", {})
    records[0].update(patch)
    return {"records": records}


def phase_e_paths() -> list[Path]:
    paths = [ROOT / value for value in PHASE_E_STATIC_PATHS if (ROOT / value).is_file()]
    review_root = ROOT / "planning/reviews/phase-e"
    if review_root.is_dir():
        paths.extend(sorted(path for path in review_root.rglob("*") if path.is_file()))
    return paths


def scan_references(paths: list[Path], refs: dict[str, Any]) -> list[tuple[str, str, str]]:
    errors: list[tuple[str, str, str]] = []
    patterns = (
        (
            "PE-001",
            r"(?<![A-Z0-9-])(UC-[A-Z0-9]+(?:-[A-Z0-9]+)*)(?![A-Z0-9-])",
            refs["use_cases"],
        ),
        (
            "PE-002",
            r"(?<![A-Z0-9-])(UCS-[A-Z0-9]+(?:-[A-Z0-9]+)*)(?![A-Z0-9-])",
            refs["slices"],
        ),
        ("PE-003", r"(?<![A-Z0-9-])(BP-\d+)", refs["processes"]),
        ("PE-004", r"(?<![A-Z0-9-])(BR-[A-Z0-9-]+)", refs["rules"]),
        ("PE-005", r"(?<![A-Z0-9-])(SE-[A-Z0-9-]+)", refs["evidence"]),
        ("PE-007", r"(?<![A-Z0-9-])(QS-[A-Z0-9-]+)", refs["quality"]),
    )
    for path in paths:
        text = path.read_text(encoding="utf-8")
        for line_number, line in enumerate(text.splitlines(), 1):
            for code, pattern, known in patterns:
                for token in re.findall(pattern, line):
                    if token not in known:
                        errors.append((code, str(path.relative_to(ROOT)), f"line {line_number}: unknown {token}"))
            for token in re.findall(r"UC-[A-Z0-9-]+/S\d+", line):
                errors.append(("PE-013", str(path.relative_to(ROOT)), f"line {line_number}: {token}"))
    return errors


def validate_repository(refs: dict[str, Any]) -> int:
    errors = 0
    paths = phase_e_paths()
    for code, location, message in scan_references(paths, refs):
        errors += 1
        diagnostic("ERROR", code, location, "reference", "Phase-E", message)

    reconciliation_path = ROOT / "planning/backlog-change-proposals/phase-e/phase-e-reconciliation.yaml"
    reconciliation = load_yaml(reconciliation_path)
    records = reconciliation.get("proposals", [])
    for index, record in enumerate(records, 1):
        for code, message in validate_record(record, refs, str(reconciliation_path), index):
            errors += 1
            diagnostic("ERROR", code, reconciliation_path.relative_to(ROOT), message, "Phase-E-record", "")

    e1_records = {
        str(record.get("epic_story_id") or record.get("future_canonical_id")): record
        for record in records
        if record.get("cohort") == "E1"
    }
    for story_id, expected_slices in EXPECTED_E1_RECORD_SLICES.items():
        actual_slices = set(list_field(e1_records.get(story_id, {}), "slice_id"))
        if actual_slices != expected_slices:
            errors += 1
            diagnostic(
                "ERROR", "PE-014", reconciliation_path.relative_to(ROOT), story_id,
                "E1-slice-contract",
                f"slices {sorted(actual_slices)} != {sorted(expected_slices)}",
            )

    selected = set(reconciliation.get("selected_cohort", {}).get("records", []))
    if selected != EXPECTED_E1_STORIES:
        errors += 1
        diagnostic(
            "ERROR", "PE-014", reconciliation_path.relative_to(ROOT), "E1",
            "cross-artifact-agreement", f"selected records {sorted(selected)} != {sorted(EXPECTED_E1_STORIES)}",
        )

    mapping_path = ROOT / "docs/data/mappings/E1-PAIN001-SINGLE-INSTRUCTION-MAPPING.yaml"
    if mapping_path.is_file():
        mapping = load_yaml(mapping_path)
        required_mapping_fields = {
            "message", "message_version", "namespace", "xml_path", "business_term",
            "cardinality", "datatype", "scheme_restriction", "validation_rule",
            "normalization_rule", "domain_concept", "domain_owner",
            "aggregate_or_entity", "database_schema", "table", "column",
            "postgresql_type", "nullable", "constraint", "index", "encrypted",
            "masked", "tenant_scoped", "rls_policy", "retention", "raw_xml_lineage",
            "source_evidence", "use_case", "slice", "test_owner",
        }
        for index, item in enumerate(mapping.get("mappings", []), 1):
            missing = sorted(required_mapping_fields - set(item))
            if missing:
                errors += 1
                diagnostic("ERROR", "PE-014", mapping_path.relative_to(ROOT), f"mapping-{index}",
                           "mapping-contract", f"missing fields {missing}")
            if item.get("use_case") != "UC-SCT-002":
                errors += 1
                diagnostic("ERROR", "PE-014", mapping_path.relative_to(ROOT), f"mapping-{index}",
                           "cross-artifact-agreement", "E1 mapping use_case must be UC-SCT-002")
            slice_id = item.get("slice")
            if slice_id not in {"UCS-SCT-002-A", "UCS-SCT-002-B"}:
                errors += 1
                diagnostic("ERROR", "PE-014", mapping_path.relative_to(ROOT), f"mapping-{index}",
                           "cross-artifact-agreement", f"unexpected E1 slice {slice_id}")
            for evidence_id in item.get("source_evidence", []) or []:
                if evidence_id not in refs["evidence"]:
                    errors += 1
                    diagnostic("ERROR", "PE-005", mapping_path.relative_to(ROOT), f"mapping-{index}",
                               "source-evidence", f"unknown evidence {evidence_id}")
        declared_total = mapping.get("review_summary", {}).get("total_entries")
        if declared_total != len(mapping.get("mappings", [])):
            errors += 1
            diagnostic("ERROR", "PE-014", mapping_path.relative_to(ROOT), "review_summary",
                       "mapping-contract", "declared total does not match mappings")

    decision_path = ROOT / "planning/reviews/phase-e/e1/E1-DECISION-REGISTER.yaml"
    if decision_path.is_file():
        decisions = load_yaml(decision_path)
        for decision in decisions.get("decisions", []):
            subject = str(decision.get("id", "UNKNOWN-DECISION"))
            classification = decision.get("source_classification")
            if isinstance(classification, list) or classification not in refs["classifications"]:
                errors += 1
                diagnostic("ERROR", "PE-009", decision_path.relative_to(ROOT), subject,
                           "source_classification", "must be one canonical scalar")
            for tag in decision.get("source_tags", []) or []:
                if tag not in AUTHORITY_TAGS or tag in refs["classifications"]:
                    errors += 1
                    diagnostic("ERROR", "PE-009", decision_path.relative_to(ROOT), subject,
                               "source_tags", f"invalid authority/discovery tag {tag}")
            for slice_id in decision.get("affected_slices", []) or []:
                if refs["slices"].get(slice_id) != "UC-SCT-002":
                    errors += 1
                    diagnostic("ERROR", "PE-002", decision_path.relative_to(ROOT), subject,
                               "slice-owner", f"unexpected E1 slice {slice_id}")
            if decision.get("review_state") not in SEMANTIC_REVIEW_STATES:
                errors += 1
                diagnostic("ERROR", "PE-011", decision_path.relative_to(ROOT), subject,
                           "review-state", "unknown semantic review state")

    approvals_path = ROOT / "planning/reviews/phase-e/e1/E1-APPROVALS.yaml"
    if approvals_path.is_file():
        approvals = load_yaml(approvals_path)
        required = set(approvals.get("required_for_canonical_migration", []))
        expected_required = REVIEW_QUEUES - {"LEGAL_REGULATORY_REVIEW"}
        if required != expected_required:
            errors += 1
            diagnostic("ERROR", "PE-011", approvals_path.relative_to(ROOT), "migration-gate",
                       "review-queues", f"required queues differ: {sorted(required)}")
        approval_records = {item.get("queue"): item for item in approvals.get("approvals", [])}
        for queue in required:
            item = approval_records.get(queue)
            if not item or item.get("review_state") not in SEMANTIC_REVIEW_STATES:
                errors += 1
                diagnostic("ERROR", "PE-011", approvals_path.relative_to(ROOT), str(queue),
                           "review-state", "missing or unknown approval record")
        all_human_approved = all(
            approval_records.get(queue, {}).get("review_state") == "HUMAN_APPROVED"
            for queue in required
        )
        gate = approvals.get("gate", {})
        if gate.get("canonical_story_migration_allowed") is True and not all_human_approved:
            errors += 1
            diagnostic("ERROR", "PE-015", approvals_path.relative_to(ROOT), "migration-gate",
                       "human-approval", "canonical migration enabled without all required approvals")

    program = (ROOT / PHASE_E_STATIC_PATHS[0]).read_text(encoding="utf-8")
    frontend = (ROOT / PHASE_E_STATIC_PATHS[2]).read_text(encoding="utf-8")
    for token in EXPECTED_E1_STORIES:
        target = program if token != "EPIC-24/24.10" else program + frontend
        if token not in target:
            errors += 1
            diagnostic("ERROR", "PE-014", "Phase-E-program/frontend", token,
                       "cross-artifact-agreement", "selected E1 story missing")

    return finish(errors, 0, validated_phase_e_files=len(paths), validated_records=len(records))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture", type=Path)
    args = parser.parse_args()
    refs = catalogs()
    if args.fixture:
        fixture = load_fixture(args.fixture)
        errors = []
        for index, record in enumerate(fixture.get("records", []), 1):
            errors.extend(validate_record(record, refs, str(args.fixture), index))
        for code, message in errors:
            diagnostic("ERROR", code, args.fixture, message, "Phase-E-fixture", "")
        return finish(len(errors), 0, validated_fixtures=1)
    return validate_repository(refs)


if __name__ == "__main__":
    sys.exit(main())
